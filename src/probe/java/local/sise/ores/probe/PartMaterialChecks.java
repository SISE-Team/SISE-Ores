package local.sise.ores.probe;

import com.mojang.serialization.JsonOps;
import ic2ce.api.recipe.*;
import java.util.*;
import local.sise.ores.MaterialCatalog;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

/** Cross-mod ordinary plates/parts, native IC2 production and dense-stage boundaries. */
final class PartMaterialChecks {
    private static final List<String> checks=new ArrayList<>();
    private static void check(boolean value,String message) {
        if(!value)throw new IllegalStateException(message); checks.add(message);
    }
    private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }
    private static Recipe<?> recipe(MinecraftServer server,String id) {
        return server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse(id))).orElseThrow().value();
    }
    private static ItemStack ic2(IBasicMachineRecipeManager manager,String input,int count,String output,int outputCount,String label) {
        var result=manager.apply(new ItemStack(item(input),count),false);
        check(result!=null && result.getAdjustedInput().isEmpty(),"native IC2 consumes exact part input "+label);
        check(result.getOutput().size()==1,"native IC2 has one expected part output "+label);
        var stack=result.getOutput().iterator().next();
        check(stack.is(item(output)) && stack.getCount()==outputCount,"native IC2 part identity and quantity "+label);
        return stack;
    }
    private static boolean miMatches(aztech.modern_industrialization.machines.recipe.MachineRecipe recipe,String input,int count) {
        var slot=new aztech.modern_industrialization.inventory.ConfigurableItemStack();
        slot.setKey(net.neoforged.neoforge.transfer.item.ItemResource.of(item(input)));slot.setAmount(count);
        return aztech.modern_industrialization.machines.components.CrafterComponent.doInputsMatch(List.of(slot),List.of(),recipe);
    }
    static List<String> verify(MinecraftServer server) {
        checks.clear();var level=server.overworld();
        for(var material:List.of("iron","gold","copper","tin","lead","bronze","steel")) {
            var ingot=List.of("iron","gold","copper").contains(material)?"minecraft:"+material+"_ingot":"sise_ores:"+material+"_ingot";
            var block=List.of("iron","gold","copper").contains(material)?"minecraft:"+material+"_block":"sise_ores:"+material+"_block";
            var plate="sise_ores:"+material+"_plate";
            ic2(Recipes.metalformerRolling.get(level),ingot,1,plate,1,material+" rolling");
            ic2(Recipes.blockcutter.get(level),block,1,plate,9,material+" block cutting");
            ic2(Recipes.compressor.get(level),plate,9,"ic2:dense_"+material+"_plate",1,material+" dense compression");
            check(Recipes.compressor.get(level).apply(new ItemStack(item(plate),8),false)==null,"eight ordinary plates cannot form a dense plate "+material);
            check(!MaterialCatalog.BY_SOURCE.containsKey("ic2:dense_"+material+"_plate"),"dense plate identity remains native "+material);
            if(!material.equals("steel")) {
                var craft=(CraftingRecipe)recipe(server,"ic2:shapeless/"+material+"_plate");
                var hammer=new ItemStack(item("ic2:forge_hammer"));hammer.setDamageValue(3);
                var input=CraftingInput.of(2,1,List.of(new ItemStack(item(ingot)),hammer));
                check(craft.matches(input,level) && craft.assemble(input).is(item(plate)) && craft.assemble(input).getCount()==1,"native workbench forging produces unified ordinary plate "+material);
                var returned=craft.getRemainingItems(input).get(1);
                check(returned.is(hammer.getItem()) && returned.getDamageValue()==4,"plate forging returns hammer with one durability consumed "+material);
            }
        }
        for(var material:List.of("iron","gold","copper","tin","lead","bronze","steel","silver","silicon","titanium")) {
            var ingot=List.of("iron","gold","copper").contains(material)?"minecraft:"+material+"_ingot":"sise_ores:"+material+"_ingot";
            var compressor=(aztech.modern_industrialization.machines.recipe.MachineRecipe)recipe(server,"modern_industrialization:materials/"+material+"/compressor/main");
            check(miMatches(compressor,ingot,1),"MI compressor accepts unified ingot "+material);
            var encoded=Recipe.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),compressor).getOrThrow().getAsJsonObject();
            var output=encoded.getAsJsonArray("item_outputs").get(0).getAsJsonObject();
            check(output.get("id").getAsString().equals("sise_ores:"+material+"_plate") && output.get("amount").getAsInt()==1,"MI compression preserves one-ingot one-plate cost "+material);
            var recycler=(aztech.modern_industrialization.machines.recipe.MachineRecipe)recipe(server,"modern_industrialization:materials/"+material+"/macerator/plate");
            var group=MaterialCatalog.BY_TARGET.get("sise_ores:"+material+"_plate");
            for(var member:group.members())check(miMatches(recycler,member,1),"MI plate recycler accepts original counterpart "+member);
            check(miMatches(recycler,group.target(),1),"MI plate recycler accepts unified counterpart "+material);
        }
        check(Recipes.metalformerRolling.get(level).apply(new ItemStack(item("oritech:biosteel_ingot")),false)==null,"IC2 steel rolling excludes distinct biosteel");
        var steelCompressor=(aztech.modern_industrialization.machines.recipe.MachineRecipe)recipe(server,"modern_industrialization:materials/steel/compressor/main");
        check(!miMatches(steelCompressor,"oritech:biosteel_ingot",1),"MI ordinary steel plate compression excludes biosteel");
        var steelPlate=(CraftingRecipe)recipe(server,"cakesticklib:steel_plate");
        check(!steelPlate.matches(CraftingInput.of(3,1,Collections.nCopies(3,new ItemStack(item("oritech:biosteel_ingot")))),level),"workbench ordinary steel plates exclude biosteel");
        for(var material:List.of("copper","gold","silver","silicon","titanium"))
            pressing(server,material+"_plate",List.of("copper","gold").contains(material)?"minecraft:"+material+"_ingot":"sise_ores:"+material+"_ingot",1);
        pressing(server,"titanium_gear","sise_ores:titanium_ingot",4);
        var carbon=MaterialCatalog.BY_SOURCE.get("ic2:carbon_plate");
        ic2(Recipes.compressor.get(level),"ic2:carbon_mesh",1,carbon.target(),1,"carbon fiber mesh compression");
        var plateTag=net.minecraft.tags.TagKey.create(Registries.ITEM,Identifier.parse(carbon.inputTag().substring(1)));
        check(new ItemStack(item(carbon.target())).is(plateTag) && !new ItemStack(item("modern_industrialization:carbon_plate")).is(plateTag),"carbon fiber composite excludes compressed carbon plate");
        var plasticPacker=(CraftingRecipe)recipe(server,"oritech:plasticblock");
        var plasticInputs=new ArrayList<ItemStack>();
        var plastics=List.of("sise_ores:plastic_sheet","cakesticklib:plastic","industrialforegoing:plastic","oritech:plastic_sheet");
        for(int i=0;i<9;i++)plasticInputs.add(new ItemStack(item(plastics.get(i%plastics.size()))));
        var plasticGrid=CraftingInput.of(3,3,plasticInputs);
        check(plasticPacker.matches(plasticGrid,level) && plasticPacker.assemble(plasticGrid).is(item("oritech:plastic")) && plasticPacker.assemble(plasticGrid).getCount()==1,"mixed original and canonical plastic sheets pack at nine-to-one");
        var plasticUnpacker=(CraftingRecipe)recipe(server,"oritech:plasticblockinv");
        var plasticBlock=CraftingInput.of(1,1,List.of(new ItemStack(item("oritech:plastic"))));
        check(plasticUnpacker.matches(plasticBlock,level) && plasticUnpacker.assemble(plasticBlock).is(item("sise_ores:plastic_sheet")) && plasticUnpacker.assemble(plasticBlock).getCount()==9,"plastic block unpacks to nine canonical sheets");
        check(item("ic2:rubber_sheet") instanceof BlockItem && !(item("modern_industrialization:rubber_sheet") instanceof BlockItem),"same-name rubber materials retain placeable block versus component behavior");
        for(var id:List.of("ae2:printed_silicon","modern_industrialization:silicon_n_doped_plate","modern_industrialization:silicon_p_doped_plate","modern_industrialization:iron_large_plate","modern_industrialization:copper_curved_plate"))
            check(!MaterialCatalog.BY_SOURCE.containsKey(id) && item(id)!=Items.AIR,"special manufactured stage remains registered and separate "+id);
        return List.copyOf(checks);
    }
    private static void pressing(MinecraftServer server,String part,String ingot,int count) {
        var press=(liedge.ltxindustries.recipe.PressingRecipe)recipe(server,"ltxi:pressing/"+part);
        var items=new net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler(1);
        items.set(0,net.neoforged.neoforge.transfer.item.ItemResource.of(item(ingot)),count);
        var input=new liedge.ltxindustries.recipe.LTXIRecipeInput(items,net.neoforged.neoforge.transfer.EmptyResourceHandler.instance(),press.getMode(),net.minecraft.core.BlockPos.ZERO);
        check(press.matches(input,server.overworld()),"native LTXI pressing accepts canonical ingot with exact quantity "+part);
        // LTXI machines generate resource outputs; their crafting assemble() is empty.
        var results=press.generateItemResults(net.minecraft.util.RandomSource.create(42));
        check(results.size()==1 && results.getFirst().resource().is(item("sise_ores:"+part)) && results.getFirst().amount()==1,"native LTXI pressing emits canonical part "+part);
        var other=(liedge.ltxindustries.recipe.PressingRecipe)recipe(server,"ltxi:pressing/"+(part.endsWith("gear")?"titanium_plate":"titanium_gear"));
        check(!press.matches(new liedge.ltxindustries.recipe.LTXIRecipeInput(items,net.neoforged.neoforge.transfer.EmptyResourceHandler.instance(),other.getMode(),net.minecraft.core.BlockPos.ZERO),server.overworld()),"LTXI plate and gear press modes remain distinct "+part);
        press.consumeItemInputs(input,net.minecraft.util.RandomSource.create(42));
        check(items.getAmountAsLong(0)==0,"native LTXI pressing consumes original recipe quantity "+part);
    }
    private PartMaterialChecks() {}
}
