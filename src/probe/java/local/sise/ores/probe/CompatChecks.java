package local.sise.ores.probe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.util.*;
import local.sise.compat.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

final class CompatChecks {
    private static final List<String> checks = new ArrayList<>();
    private static void check(boolean value,String message) { if (!value) throw new IllegalStateException(message); checks.add(message); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }
    private static Recipe<?> recipe(MinecraftServer server,String id) { return server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse(id))).orElseThrow(()->new IllegalStateException("Missing recipe "+id)).value(); }
    private static ItemStack stack(MinecraftServer server,JsonElement json) {
        if (json.isJsonObject() && json.getAsJsonObject().has("item") && json.getAsJsonObject().get("item").isJsonPrimitive()) return ic2ce.neoforge.recipe.codec.RecipeValueCodec.parseOutput(json.getAsJsonObject());
        return ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
    }
    private static ItemStack sample(MinecraftServer server,JsonElement json) {
        if (json.isJsonObject() && (json.getAsJsonObject().has("tag") || json.getAsJsonObject().has("item"))
                && json.getAsJsonObject().keySet().stream().allMatch(k->Set.of("item","tag","count").contains(k))) {
            var input=ic2ce.neoforge.recipe.codec.RecipeValueCodec.parseInput(json);
            return input.getInputs().stream().filter(s->BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("sise_ores"))
                .findFirst().orElseGet(()->input.getInputs().getFirst()).copyWithCount(input.getAmount());
        }
        if (json.isJsonObject() && json.getAsJsonObject().has("conduit_type")) {
            var o = new JsonObject(); o.addProperty("id","enderio:conduit");
            var components = new JsonObject(); components.add("enderio:conduit",json.getAsJsonObject().get("conduit_type"));
            o.add("components",components); return stack(server,o);
        }
        var ing = Ingredient.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
        return RecipeAudit.sample(ing,1);
    }
    private static boolean miMatches(aztech.modern_industrialization.machines.recipe.MachineRecipe recipe,ItemStack... stacks) {
        var slots = new ArrayList<aztech.modern_industrialization.inventory.ConfigurableItemStack>();
        for (var stack : stacks) {
            var slot = new aztech.modern_industrialization.inventory.ConfigurableItemStack();
            slot.setKey(net.neoforged.neoforge.transfer.item.ItemResource.of(stack)); slot.setAmount(stack.getCount()); slots.add(slot);
        }
        return aztech.modern_industrialization.machines.components.CrafterComponent.doInputsMatch(slots,List.of(),recipe);
    }
    static List<String> verify(MinecraftServer server) {
        checks.clear();
        check(((Map<?,?>)RecipePatches.lastReport.get("skipped")).isEmpty(),"all compatibility source guards matched");
        check(((Map<?,?>)RecipePatches.lastReport.get("applied")).size()==RecipePatches.RULES.getAsJsonObject("patches").size(),"all currently installed compatibility patches applied");
        check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse("avaritia:eio_creative_power"))).isEmpty(),"missing EnderIO creative power disabled by registry conditions");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        int restored = 0;
        for (var entry : RecipePatches.RULES.getAsJsonObject("patches").entrySet()) {
            String id = entry.getKey(); if (id.equals("avaritia:eio_creative_power")) continue;
            var after = local.sise.ores.RecipeTransform.transform(entry.getValue().getAsJsonObject().getAsJsonObject("after")).json();
            var r = recipe(server,id);
            check(r!=null,"patched recipe loaded "+id);
            if ((id.startsWith("enderio_evolution:") || id.startsWith("neoecoae:") || id.startsWith("avaritia:")) && !entry.getValue().getAsJsonObject().has("category")) restored++;
            if (after.has("pattern")) {
                var pattern = after.getAsJsonArray("pattern"); var stacks = new ArrayList<ItemStack>();
                int width = pattern.get(0).getAsString().length();
                for (var row : pattern) for (char c : row.getAsString().toCharArray())
                    stacks.add(c==' '?ItemStack.EMPTY:sample(server,after.getAsJsonObject("key").get(Character.toString(c))));
                var expected = stack(server,after.get("result"));
                if (r instanceof committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe avaritia) {
                    var input = committee.nova.mods.avaritia.api.common.crafting.TierInput.of(width,pattern.size(),stacks,4);
                    check(avaritia.matches(input,server.overworld()),"tier 4 recipe matches original expensive layout "+id);
                    var output = avaritia.assemble(input);
                    check(ItemStack.matches(expected,output),"creative item and components preserved "+id);
                    check(!avaritia.matches(committee.nova.mods.avaritia.api.common.crafting.TierInput.of(width,pattern.size(),stacks,3),server.overworld()),"lower table tier rejected "+id);
                    avaritia.getRemainingItems(input);
                } else {
                    var craft = (CraftingRecipe)r; var input = CraftingInput.of(width,pattern.size(),stacks);
                    check(craft.matches(input,server.overworld()) && ItemStack.matches(expected,craft.assemble(input)),"reviewed crafting layout executes "+id);
                    var mirrored = new ArrayList<ItemStack>();
                    for (int y=0;y<pattern.size();y++) for (int x=0;x<width;x++) mirrored.add(stacks.get(y*width+width-1-x));
                    check(craft.matches(CraftingInput.of(width,pattern.size(),mirrored),server.overworld()),"mirrored layout remains valid "+id);
                    for (var h : server.getRecipeManager().getRecipes()) if (h.value() instanceof CraftingRecipe other && other.matches(input,server.overworld()))
                        check(ItemStack.matches(expected,other.assemble(input)),"no different crafting output for new layout "+id+" / "+h.id().identifier());
                    // A conduit upgrade must reject the same base item with the wrong/missing conduit component.
                    for (int i=0;i<stacks.size();i++) if (stacks.get(i).is(item("enderio:conduit"))) {
                        var wrong = new ArrayList<>(stacks); wrong.set(i,new ItemStack(item("enderio:conduit")));
                        check(!craft.matches(CraftingInput.of(width,pattern.size(),wrong),server.overworld()),"untyped conduit rejected "+id);
                        wrong.set(i,expected.copy());
                        check(!craft.matches(CraftingInput.of(width,pattern.size(),wrong),server.overworld()),"wrong conduit tier rejected "+id);
                    }
                }
            }
        }
        check(restored==21,"21 failing recipes restored; one absent-item recipe remains conditional");
        var cutter = (com.glodblock.github.extendedae.recipe.CircuitCutterRecipe)recipe(server,"neoecoae:circuit_cutter/superconducting_processor_print");
        var block = new ItemStack(item("neoecoae:energized_superconductive_block"));
        check(cutter.energy==2000 && cutter.getInput().getIngredient().test(block) && cutter.output.create().getCount()==9,"circuit cutter input/energy/nine prints verified");
        cutter.getInput().sample().consume(block); check(block.isEmpty(),"circuit cutter consumes one block");
        check(cutter.getInput().getAmount()==1,"cutter recipe template remains immutable during consumption test");
        var assembler = (com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe)recipe(server,"neoecoae:crystal_assembler/superconducting_processor");
        check(assembler.energy==2000 && assembler.output.create().getCount()==4,"crystal assembler energy/four processors verified");
        for (var in : assembler.getInputs()) {
            var stack = RecipeAudit.sample(in.getIngredient(),in.getAmount());
            check(in.getAmount()==4 && in.getIngredient().test(stack),"processor assembler four-item input matches");
            in.sample().consume(stack); check(stack.isEmpty(),"processor assembler consumes exact input count");
            check(in.getAmount()==4,"assembler recipe template remains immutable during consumption test");
        }
        for (var tier : List.of("lv_mv","mv_hv","hv_ev","ev_supra")) {
            String stem = "modern_industrialization:electric_age/transformer/"+tier;
            var upJson = RecipePatches.RULES.getAsJsonObject("patches").getAsJsonObject(stem+"/down").getAsJsonObject("after").getAsJsonArray("ingredients").get(0).getAsJsonObject();
            var up = new ItemStack(item(upJson.get("item").getAsString()));
            var craft = (CraftingRecipe)recipe(server,stem+"/down");
            var input = CraftingInput.of(1,1,List.of(up));
            check(craft.matches(input,server.overworld()),"transformer forward conversion matches "+tier);
            var down = craft.assemble(input); check(down.getCount()==1,"one transformer preserved "+tier);
            var reverse = (StonecutterRecipe)recipe(server,"sise_compat:manual/transformer/"+tier);
            check(reverse.matches(new SingleRecipeInput(down),server.overworld()) && ItemStack.matches(reverse.assemble(new SingleRecipeInput(down)),up),"transformer reversible 1:1 "+tier);
            var machine = (aztech.modern_industrialization.machines.recipe.MachineRecipe)recipe(server,"modern_industrialization:assembler_generated/electric_age/transformer/"+tier+"/down");
            check(miMatches(machine,up),"MI direction conversion accepts plain transformer "+tier);
            var base = (aztech.modern_industrialization.machines.recipe.MachineRecipe)recipe(server,"modern_industrialization:assembler_generated/electric_age/transformer/"+tier+"/up");
            var rawInputs = base.itemInputs.stream().map(i->RecipeAudit.sample(i.ingredient(),i.amount())).toArray(ItemStack[]::new);
            check(miMatches(base,rawInputs) && !miMatches(machine,rawInputs),"raw transformer components select one assembler recipe "+tier);
            var named = up.copy(); named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep this machine's state"));
            check(!craft.matches(CraftingInput.of(1,1,List.of(named)),server.overworld()) && !miMatches(machine,named),"custom machine data protected in crafting and assembler "+tier);
            for (var ordinary : List.of(up,down)) {
                var custom=ordinary.copy();custom.set(DataComponents.CUSTOM_NAME,Component.literal("Keep configured machine"));
                var customInput=CraftingInput.of(1,1,List.of(custom));
                var bypasses=server.getRecipeManager().getRecipes().stream().filter(holder->holder.value() instanceof CraftingRecipe c && c.matches(customInput,server.overworld())).map(holder->holder.id().identifier().toString()).toList();
                check(bypasses.isEmpty(),"no alternate crafting recipe bypasses transformer data protection "+tier+" "+BuiltInRegistries.ITEM.getKey(ordinary.getItem())+" "+bypasses);
            }
            var ing = new PristineIngredient(up.getItem()).toVanilla();
            var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),server.registryAccess(),net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
            try {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer,ing);
                var decoded = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                check(decoded.test(up) && !decoded.test(named) && buffer.readableBytes()==0,"conversion ingredient network round trip "+tier);
            } finally { buffer.release(); }
        }
        for (var type : aztech.modern_industrialization.machines.init.MIMachineRecipeTypes.getRecipeTypes())
            for (var holder : type.getRecipesWithCache(server.overworld())) {
                String id = holder.id().identifier().toString();
                check(!id.startsWith("sise_compat:") && !id.contains("charcoal_9x9"),"manual compatibility conversion not imported into MI "+id);
            }
        return List.copyOf(checks);
    }
}
