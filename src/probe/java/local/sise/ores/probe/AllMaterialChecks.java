package local.sise.ores.probe;

import com.mojang.serialization.JsonOps;
import ic2ce.api.recipe.*;
import ic2ce.neoforge.recipe.codec.RecipeHolder;
import java.util.*;
import local.sise.ores.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Full material sweep and native IC2CE machine API checks, including resource reload. */
final class AllMaterialChecks {
    private static final List<String> checks = new ArrayList<>();
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message); checks.add(message);
    }
    private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }
    private static IBasicMachineRecipeManager manager(String type, MinecraftServer server) {
        var level=server.overworld();
        return switch(type) {
            case "ic2:macerator" -> Recipes.macerator.get(level);
            case "ic2:extractor" -> Recipes.extractor.get(level);
            case "ic2:compressor" -> Recipes.compressor.get(level);
            case "ic2:centrifuge" -> Recipes.centrifuge.get(level);
            case "ic2:ore_washer" -> Recipes.oreWashing.get(level);
            case "ic2:blast_furnace" -> Recipes.blastfurnace.get(level);
            case "ic2:block_cutter" -> Recipes.blockcutter.get(level);
            case "ic2:metal_former_rolling" -> Recipes.metalformerRolling.get(level);
            case "ic2:metal_former_cutting" -> Recipes.metalformerCutting.get(level);
            case "ic2:metal_former_extruding" -> Recipes.metalformerExtruding.get(level);
            default -> null;
        };
    }
    static List<String> verify(MinecraftServer server) {
        checks.clear();
        for (var material : MaterialCatalog.MATERIALS) if (material.owned()) {
            var target=new ItemStack(item(material.target()));
            var preferred=new ItemStack(item(material.preferred()));
            for (var type : List.of(RecipeType.SMELTING,RecipeType.BLASTING,RecipeType.SMOKING))
                check(target.getBurnTime(type,server.overworld().fuelValues())==preferred.getBurnTime(type,server.overworld().fuelValues()),"preferred source fuel value preserved "+material.name()+" "+type);
        }
        int machines=0, canonicalInputs=0, workbench=0;
        for (var holder : server.getRecipeManager().getRecipes()) {
            if (!holder.id().identifier().getNamespace().equals("ic2")) continue;
            var recipe=holder.value();
            var type=BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();
            var nativeManager=manager(type,server);
            if (recipe instanceof RecipeHolder<?,?> machine && machine.recipe().getInput() instanceof IRecipeInput input && nativeManager!=null) {
                for (var sample : input.getInputs()) {
                    var material=MaterialCatalog.BY_SOURCE.get(BuiltInRegistries.ITEM.getKey(sample.getItem()).toString());
                    if (material==null) continue;
                    var canonical=new ItemStack(item(material.target()),input.getAmount());
                    check(input.matches(canonical),"IC2 input accepts canonical counterpart "+holder.id()+" "+material.name());
                    var result=nativeManager.apply(canonical,false);
                    check(result!=null && result.getAdjustedInput().isEmpty(),"native IC2 API consumes exact quantity "+holder.id()+" "+material.name());
                    for (var output : result.getOutput())
                        check(!MaterialCatalog.BY_SOURCE.containsKey(BuiltInRegistries.ITEM.getKey(output.getItem()).toString()),"native IC2 API emits canonical output or distinct intermediate "+holder.id());
                    canonicalInputs++;
                }
                if (machine.recipe().getOutput() instanceof Collection<?> outputs)
                    for (var output : outputs) if (output instanceof ItemStack stack)
                        check(!MaterialCatalog.BY_SOURCE.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()),"IC2 decoded material output unified "+holder.id());
                machines++;
            } else if (recipe instanceof CraftingRecipe craft && ((Map<?,?>)RecipeUnifier.lastReport.get("recipes")).containsKey(holder.id().identifier().toString())) {
                var json=Recipe.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),recipe).getOrThrow().getAsJsonObject();
                var stacks=new ArrayList<ItemStack>(); int width, height;
                if (json.has("pattern")) {
                    var pattern=json.getAsJsonArray("pattern"); width=pattern.get(0).getAsString().length(); height=pattern.size();
                    for (var row : pattern) for (char key : row.getAsString().toCharArray())
                        stacks.add(key==' ' ? ItemStack.EMPTY : sample(json.getAsJsonObject("key").get(String.valueOf(key))));
                } else if (json.has("ingredients")) {
                    for(var input : json.getAsJsonArray("ingredients")) stacks.add(sample(input));
                    width=Math.min(3,stacks.size()); height=(stacks.size()+width-1)/width;
                    while(stacks.size()<width*height) stacks.add(ItemStack.EMPTY);
                } else continue;
                if(stacks.stream().anyMatch(Objects::isNull)) continue;
                var grid=CraftingInput.of(width,height,stacks);
                check(craft.matches(grid,server.overworld()),"IC2 crafting accepts canonical sample "+holder.id());
                check(!craft.assemble(grid).isEmpty(),"IC2 crafting assembles real output "+holder.id());
                workbench++;
            }
        }
        check(machines>200 && canonicalInputs>100 && workbench>20,"native IC2 sweep covers machines, canonical inputs and workbench recipes: "+machines+"/"+canonicalInputs+"/"+workbench);
        hammerRecipe(server,"ic2:shapeless/uranium_238","sise_ores:uranium_block","ic2:uranium_238");
        hammerRecipe(server,"modern_industrialization:materials/sulfur/craft/tiny_dust_from_dust","sise_ores:sulfur_dust","sise_ores:sulfur_tiny_dust");
        for(var id:local.sise.compat.materials.RecipeSafety.DUST_SPLITS) if(id.startsWith("oritech:")) {
            var holder=server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,Identifier.parse(id))).orElseThrow();
            check(holder.value() instanceof CraftingRecipe,"original powder split restored to workbench "+id);
            var craft=(CraftingRecipe)holder.value();
            var stack=RecipeAudit.sample(craft.placementInfo().ingredients().getFirst(),1);
            var grid=CraftingInput.of(1,1,List.of(stack));
            check(craft.matches(grid,server.overworld()) && craft.assemble(grid).getCount()==9,"restored powder split consumes one whole powder and produces nine tiny powders "+id);
            var expected=craft.assemble(grid);
            for(var other:server.getRecipeManager().getRecipes()) if(other.value().getType()==RecipeType.CRAFTING && other.value() instanceof CraftingRecipe candidate && candidate.matches(grid,server.overworld()))
                check(ItemStack.matches(expected,candidate.assemble(grid)),"all single-powder crafting outputs agree after unification "+id+" / "+other.id());
        }
        return List.copyOf(checks);
    }
    private static void hammerRecipe(MinecraftServer server,String id,String material,String output) {
        var recipe=server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,Identifier.parse(id))).orElseThrow().value();
        check(recipe instanceof CraftingRecipe,"conflict resolved as workbench crafting "+id);
        var craft=(CraftingRecipe)recipe;
        var hammer=new ItemStack(item("ic2:forge_hammer"));hammer.setDamageValue(7);
        hammer.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Keep my hammer"));
        var grid=CraftingInput.of(2,1,List.of(new ItemStack(item(material)),hammer));
        check(craft.matches(grid,server.overworld()) && craft.assemble(grid).is(item(output)) && craft.assemble(grid).getCount()==9,"workbench material plus hammer retains original 1:9 result "+id);
        check(!craft.matches(CraftingInput.of(1,1,List.of(new ItemStack(item(material)))),server.overworld()),"plain material cannot accidentally select hammer conversion "+id);
        var remainders=craft.getRemainingItems(grid);
        check(remainders.get(0).isEmpty() && remainders.get(1).is(hammer.getItem()) && remainders.get(1).getDamageValue()==8,"hammer returned with one durability consumed "+id);
        check(remainders.get(1).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).equals(hammer.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) && hammer.getDamageValue()==7,"hammer custom data and recipe input remain intact "+id);
        var lastUse=hammer.copy();lastUse.setDamageValue(lastUse.getMaxDamage()-1);
        var lastGrid=CraftingInput.of(2,1,List.of(new ItemStack(item(material)),lastUse));
        check(craft.matches(lastGrid,server.overworld()) && craft.getRemainingItems(lastGrid).get(1).isEmpty(),"hammer breaks on its final use "+id);
        var expected=craft.assemble(grid);
        for(var other:server.getRecipeManager().getRecipes()) if(other.value().getType()==RecipeType.CRAFTING && other.value() instanceof CraftingRecipe candidate && candidate.matches(grid,server.overworld()))
            check(ItemStack.matches(expected,candidate.assemble(grid)),"hammer layout has one unambiguous workbench result "+id+" / "+other.id());
    }
    private static ItemStack sample(com.google.gson.JsonElement json) {
        var input=ic2ce.neoforge.recipe.codec.RecipeValueCodec.parseInput(json);
        var stack=input.getInputs().stream().filter(s->BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("sise_ores"))
            .findFirst().orElseGet(()->input.getInputs().isEmpty()?null:input.getInputs().getFirst());
        return stack==null ? null : stack.copyWithCount(input.getAmount());
    }
    private AllMaterialChecks() {}
}
