package local.sise.ores.probe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import local.sise.ores.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

/** Disposable audit instrumentation; never included in the installed mod. */
final class RecipeAudit {
    static final Map<String, JsonElement> before = new TreeMap<>();
    static Map<String, JsonElement> firstLoaded;
    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RecipeAudit::capture);
    }
    static void capture(ModifyRecipeJsonsEvent event) {
        before.clear();
        event.getRecipeJsons().forEach((id, json) -> before.put(id.toString(), json.deepCopy()));
    }
    static void export(MinecraftServer server, Path directory) {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var loaded = new TreeMap<String, JsonElement>();
        var original = new TreeMap<String, JsonElement>();
        var originalErrors = new TreeMap<String, String>();
        var encodeErrors = new TreeMap<String, String>();
        var classes = new TreeMap<String, String>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            String id = holder.id().identifier().toString();
            try {
                loaded.put(id, Recipe.CODEC.encodeStart(ops, holder.value()).getOrThrow());
                classes.put(id, holder.value().getClass().getName());
            } catch (Throwable e) { encodeErrors.put(id, e.toString()); }
        }
        for (var entry : before.entrySet()) {
            try {
                var recipe = Recipe.CODEC.parse(ops, entry.getValue()).getOrThrow();
                original.put(entry.getKey(), Recipe.CODEC.encodeStart(ops, recipe).getOrThrow());
            } catch (Throwable e) { originalErrors.put(entry.getKey(), e.toString()); }
        }
        var tags = new TreeMap<String, List<String>>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> tags.put(tag.key().location().toString(),
            tag.stream().map(h -> BuiltInRegistries.ITEM.getKey(h.value()).toString()).sorted().toList()));
        var data = new TreeMap<String, Object>();
        data.put("loaded", loaded); data.put("before", original); data.put("raw_before", before);
        firstLoaded = loaded;
        data.put("before_decode_errors", originalErrors); data.put("encode_errors", encodeErrors);
        data.put("item_tags", tags); data.put("classes", classes);
        data.put("items", BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().toList());
        data.put("execution", execute(server, directory));
        var proxies = new TreeMap<String, JsonElement>();
        for (var type : aztech.modern_industrialization.machines.init.MIMachineRecipeTypes.getRecipeTypes()) {
            for (var holder : type.getRecipesWithCache(server.overworld())) {
                proxies.put(holder.id().identifier().toString(), Recipe.CODEC.encodeStart(ops, holder.value()).getOrThrow());
                if (RecipeSafety.manualOnly(holder.id().identifier().toString().replace("/convert/", "convert/")))
                    throw new IllegalStateException("Manual conversion leaked into MI: " + holder.id());
            }
        }
        data.put("mi_effective_recipes", proxies);
        try { Files.writeString(directory.resolve("recipe-audit-export.json"), new GsonBuilder().setPrettyPrinting().create().toJson(data)); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static void verifyReload(MinecraftServer server, Path directory) {
        var loaded = new TreeMap<String, JsonElement>();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var holder : server.getRecipeManager().getRecipes())
            loaded.put(holder.id().identifier().toString(), Recipe.CODEC.encodeStart(ops, holder.value()).getOrThrow());
        require(loaded.equals(firstLoaded), "Full recipe registry changed after reload");
        try {
            var path = directory.resolve("recipe-audit-export.json");
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            json.addProperty("reload_identical", true);
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static ItemStack sample(Ingredient ingredient, int count) {
        var candidates = ingredient.items().map(h -> new ItemStack(h.value(), count)).filter(ingredient::test).toList();
        return candidates.stream().filter(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("sise_ores"))
            .findFirst().orElseGet(() -> candidates.isEmpty() ? ItemStack.EMPTY : candidates.getFirst());
    }
    static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    static Map<String, Object> execute(MinecraftServer server, Path directory) {
        var counts = new TreeMap<String, Integer>();
        var failures = new TreeMap<String, String>();
        var skipped = new TreeMap<String, String>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            String id = holder.id().identifier().toString();
            var r = holder.value(); String family = "";
            try {
                if (r instanceof ShapedRecipe || r instanceof ShapelessRecipe) {
                    var craft = (CraftingRecipe)r;
                    List<ItemStack> stacks;
                    int width, height;
                    if (r instanceof ShapedRecipe shaped) {
                        width = shaped.getWidth(); height = shaped.getHeight();
                        stacks = shaped.getIngredients().stream().map(i -> i.map(v -> sample(v, 1)).orElse(ItemStack.EMPTY)).toList();
                    } else {
                        stacks = new ArrayList<>(r.placementInfo().ingredients().stream().map(i -> sample(i, 1)).toList());
                        width = Math.min(3, stacks.size()); height = (stacks.size() + width - 1) / width;
                        while (stacks.size() < width * height) stacks.add(ItemStack.EMPTY);
                    }
                    var input = CraftingInput.of(width, height, stacks);
                    if (!craft.matches(input, server.overworld())) { skipped.put(id, "No matching representative for empty/custom/component ingredient"); continue; }
                    require(!craft.assemble(input).isEmpty(), "Matching crafting recipe produced empty output");
                    craft.getRemainingItems(input);
                    family = "crafting_match_assemble_remainder";
                } else if (r instanceof SingleItemRecipe single) {
                    var stack = sample(single.input(), 1);
                    if (stack.isEmpty()) { skipped.put(id, "Empty/custom item ingredient"); continue; }
                    var input = new SingleRecipeInput(stack);
                    require(single.matches(input, server.overworld()), "Single-input mismatch");
                    require(!single.assemble(input).isEmpty(), "Empty single-input output");
                    family = "single_item_match_assemble";
                } else if (r instanceof mekanism.api.recipes.ItemStackToItemStackRecipe mek) {
                    var sized = mek.getInput().ingredient();
                    var stack = sample(sized.ingredient(), sized.count());
                    if (stack.isEmpty()) { skipped.put(id, "Empty MEK ingredient"); continue; }
                    var input = new SingleRecipeInput(stack);
                    require(mek.matches(input, server.overworld()), "MEK mismatch");
                    require(!mek.assemble(input).isEmpty(), "Empty MEK output");
                    family = "mek_match_assemble";
                } else if (r instanceof rearth.oritech.init.recipes.OritechRecipe ori) {
                    var stacks = ori.itemInputs().stream().map(i -> sample(i, 1)).toList();
                    if (stacks.stream().anyMatch(ItemStack::isEmpty)) { skipped.put(id, "Empty Oritech ingredient"); continue; }
                    var fluid = net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                    if (ori.fluidInput().isPresent()) {
                        var in = ori.fluidInput().get(); var fluids = in.ingredient().fluids();
                        if (fluids.isEmpty()) { skipped.put(id, "Empty Oritech fluid ingredient"); continue; }
                        fluid = new net.neoforged.neoforge.fluids.FluidStack(fluids.getFirst().value(), in.amount());
                    }
                    var input = new rearth.oritech.init.recipes.OritechRecipeInput(stacks, fluid);
                    require(ori.matches(input, server.overworld()), "Oritech mismatch");
                    for (var output : ori.itemResults()) require(!output.create().isEmpty(), "Empty Oritech output");
                    family = "oritech_match_output_templates";
                } else if (r instanceof aztech.modern_industrialization.machines.recipe.MachineRecipe mi) {
                    var items = new ArrayList<aztech.modern_industrialization.inventory.ConfigurableItemStack>();
                    for (var in : mi.itemInputs) {
                        var stack = sample(in.ingredient(), in.amount());
                        if (stack.isEmpty()) throw new IllegalStateException("Empty MI item ingredient");
                        var conf = new aztech.modern_industrialization.inventory.ConfigurableItemStack();
                        conf.setKey(net.neoforged.neoforge.transfer.item.ItemResource.of(stack.getItem()));
                        conf.setAmount(in.amount()); items.add(conf);
                    }
                    var fluids = new ArrayList<aztech.modern_industrialization.inventory.ConfigurableFluidStack>();
                    for (var in : mi.fluidInputs) {
                        var fluid = in.getInputFluids().stream().findFirst().orElseThrow();
                        var conf = new aztech.modern_industrialization.inventory.ConfigurableFluidStack(Integer.MAX_VALUE);
                        conf.setKey(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid));
                        conf.setAmount(in.amount()); fluids.add(conf);
                    }
                    require(aztech.modern_industrialization.machines.components.CrafterComponent.doInputsMatch(items, fluids, mi), "MI input matcher rejected sample");
                    family = "mi_crafter_input_match";
                } else if (r instanceof com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe sag) {
                    var stack = sample(sag.input(), 1);
                    if (stack.isEmpty()) { skipped.put(id, "Empty SAG ingredient"); continue; }
                    // Grinding-ball bonuses do not participate in ingredient matching.
                    var input = new com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe.Input(stack, null);
                    require(sag.matches(input, server.overworld()), "SAG mismatch");
                    family = "enderio_sag_input_match";
                } else { skipped.put(id, "Codec/tag audit only; custom execution API"); continue; }
                counts.merge(family, 1, Integer::sum);
            } catch (Throwable e) { failures.put(id, e.toString()); }
        }
        var confirmed = new ArrayList<JsonObject>();
        var witnesses = directory.resolve("crafting-witnesses.json");
        if (Files.isRegularFile(witnesses)) try {
            for (var element : JsonParser.parseString(Files.readString(witnesses)).getAsJsonArray()) {
                var test = element.getAsJsonObject(); var stacks = new ArrayList<ItemStack>();
                for (var item : test.getAsJsonArray("items")) stacks.add(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item.getAsString()))));
                var input = CraftingInput.of(test.get("width").getAsInt(), test.get("height").getAsInt(), stacks);
                var outputs = new ArrayList<ItemStack>();
                for (var id : test.getAsJsonArray("recipes")) {
                    var h = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id.getAsString())));
                    if (h.isPresent() && h.get().value() instanceof CraftingRecipe c && c.matches(input, server.overworld())) outputs.add(c.assemble(input));
                }
                if (outputs.size() == 2 && (!ItemStack.isSameItemSameComponents(outputs.get(0), outputs.get(1)) || outputs.get(0).getCount() != outputs.get(1).getCount())) confirmed.add(test);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return Map.of("passed_by_family", counts, "failures", failures, "skipped", skipped, "confirmed_crafting_conflicts", confirmed);
    }
}
