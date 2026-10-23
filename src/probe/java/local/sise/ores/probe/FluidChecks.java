package local.sise.ores.probe;

import com.mojang.serialization.JsonOps;
import java.util.*;
import local.sise.ores.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import mekanism.api.chemical.*;

/** Native transport, serialization, bucket and machine-reference checks, before/after reload. */
final class FluidChecks {
    private static final List<String> checks=new ArrayList<>();
    private static void check(boolean value,String label){if(!value)throw new IllegalStateException(label);checks.add(label);}
    private static Fluid fluid(String id){return BuiltInRegistries.FLUID.getValue(Identifier.parse(id));}
    private static Item item(String id){return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));}
    static List<String> verify(MinecraftServer server){
        checks.clear();var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(var entry:FluidCatalog.FLUIDS){
            var target=fluid(entry.target());
            check(target!=null && target!=Fluids.EMPTY,"SISE fluid registered "+entry.name());
            check(target.getFluidType()==fluid(entry.preferred()).getFluidType(),"preferred fluid physical/render type preserved "+entry.name());
            check(new FluidStack(fluid("sise_ores:flowing_"+entry.name()),250).getFluid()==target,"owned flowing fluid normalizes to the same stored source "+entry.name());
            for(var source:entry.aliases()){
                var original=fluid(source);var holder=BuiltInRegistries.FLUID.wrapAsHolder(original);
                check(original.isSame(target) && target.isSame(original),"native fluid equality recognizes reviewed equivalence in both directions "+source);
                for(int amount:List.of(1,97,250,1000)){
                    var stack=new FluidStack(original,amount);
                    check(stack.getFluid()==target && stack.getAmount()==amount,"legacy fluid identity normalized without changing mB "+source+" "+amount);
                    check(new FluidStackTemplate(original,amount).create().getFluid()==target,"fluid output template normalized "+source);
                    var resource=FluidResource.of(original);
                    check(resource.equals(FluidResource.of(target)) && resource.hashCode()==FluidResource.of(target).hashCode(),"native transfer resource identity and hash agree "+source);
                    check(resource.is(holder) && resource.is(original) && stack.is(holder) && stack.is(original),"native exact fluid comparisons accept source identity "+source);
                    check(FluidIngredient.of(original).test(new FluidStack(target,amount)),"native fluid ingredient accepts SISE counterpart "+source);
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Keep fluid metadata"));
                    var json=FluidStack.CODEC.encodeStart(ops,stack).getOrThrow();
                    var decoded=FluidStack.CODEC.parse(ops,json).getOrThrow();
                    check(FluidStack.matches(stack,decoded) && decoded.getAmount()==amount,"fluid codec preserves components and exact volume "+source);
                }
            }
            for(var tag:BuiltInRegistries.FLUID.getTags().toList())if(tag.stream().anyMatch(h->entry.aliases().contains(BuiltInRegistries.FLUID.getKey(h.value()).toString())))
                check(tag.contains(BuiltInRegistries.FLUID.wrapAsHolder(target)),"fluid tag inheritance "+tag.key().location()+" "+entry.name());
            var bucket=new ItemStack(item(entry.target()+"_bucket"));
            var contained=bucketContents(bucket);
            check(contained.getFluid()==target && contained.getAmount()==1000,"SISE bucket holds exactly 1000 mB "+entry.name());
            for(var source:entry.buckets()){
                var old=bucketContents(new ItemStack(item(source)));
                check(old.getFluid()==target && old.getAmount()==1000,"legacy bucket capability emits canonical fluid "+source);
            }
            var tank=new net.neoforged.neoforge.fluids.capability.templates.FluidTank(4000);
            check(tank.fill(new FluidStack(fluid(entry.members().getFirst()),1000),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==1000,"native tank accepts source fluid "+entry.name());
            check(tank.fill(new FluidStack(target,250),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)==250 && tank.getFluidAmount()==1000,"simulated insertion does not mutate volume "+entry.name());
            var drained=tank.drain(333,net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            check(drained.getFluid()==target && drained.getAmount()==333 && tank.getFluidAmount()==667,"native tank drain conserves fluid volume "+entry.name());
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),server.registryAccess());
            try {
                FluidStack.STREAM_CODEC.encode(buffer,new FluidStack(target,137));
                var packet=FluidStack.STREAM_CODEC.decode(buffer);
                check(packet.getFluid()==target && packet.getAmount()==137 && buffer.readableBytes()==0,"network codec consumes exactly one canonical fluid payload "+entry.name());
            }finally{buffer.release();}
            var pos=new net.minecraft.core.BlockPos(32+FluidCatalog.FLUIDS.indexOf(entry)*2,100,32);
            var previous=server.overworld().getBlockState(pos);var block=UnifiedFluids.BLOCKS.get(entry.name()).get();
            server.overworld().setBlock(pos,block.defaultBlockState(),3);
            var picked=((net.minecraft.world.level.block.BucketPickup)block).pickupBlock(null,server.overworld(),pos,server.overworld().getBlockState(pos));
            check(picked.is(bucket.getItem()) && picked.getCount()==1,"world fluid source pickup produces SISE bucket "+entry.name());
            server.overworld().setBlock(pos,previous,3);
        }
        var chemicalOld=mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(Identifier.parse("mekmm:uu_matter")).orElseThrow();
        var chemicalNew=mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(Identifier.parse("sise_ores:uu_matter")).orElseThrow();
        var chemical=new ChemicalStack(chemicalOld,500);
        check(chemical.getChemical()==chemicalNew.value() && chemical.amount()==500,"UU chemical source normalized with 500 units retained");
        check(chemical.is(chemicalOld) && ChemicalResource.of(chemicalOld).is(chemicalOld),"native UU chemical comparisons accept legacy holder");
        check(mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess.chemicalStack().fromHolder(chemicalOld,500).test(chemical),"native UU chemical ingredient accepts canonical UU");
        check(ic2ce.neoforge.material.MaterialItems.UU_MATTER==item("sise_ores:uu_matter"),"IC2 hardcoded solid UU production uses SISE item");
        check(com.jerry.mekmm.common.registries.MoreMachineItems.UU_MATTER.asItem()==item("sise_ores:uu_matter"),"MekMM hardcoded solid UU reference uses SISE item");
        var amplifier=ic2ce.api.recipe.Recipes.matterFabricator.get(server.overworld()).apply(new ItemStack(item("sise_ores:scrap")),false);
        check(amplifier!=null && amplifier.getAdjustedInput().isEmpty() && amplifier.getOutput()==5000,"native IC2 amplifier accepts unified scrap with original 5000 value");
        var boxCraft=(net.minecraft.world.item.crafting.CraftingRecipe)server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse("ic2:shaped/scrap_box"))).orElseThrow().value();
        var nine=net.minecraft.world.item.crafting.CraftingInput.of(3,3,Collections.nCopies(9,new ItemStack(item("sise_ores:scrap"))));
        check(boxCraft.matches(nine,server.overworld()) && boxCraft.assemble(nine).is(item("sise_ores:scrap_box")),"nine unified scraps retain the basic IC2 box recipe");
        check(ic2ce.api.recipe.Recipes.scrapboxDrops.apply(new ItemStack(item("sise_ores:compressed_scrap_box")),true)==null,"compressed 64-scrap box cannot use the distinct nine-scrap box operation");
        for(var output:ic2ce.api.recipe.Recipes.scrapboxDrops.getDrops().keySet())
            check(!MaterialCatalog.BY_SOURCE.containsKey(BuiltInRegistries.ITEM.getKey(output.getItem()).toString()),"hardcoded scrapbox drop normalized "+BuiltInRegistries.ITEM.getKey(output.getItem()));
        check(ic2ce.neoforge.registry.FluidDefinitions.UU_MATTER.still==fluid("sise_ores:uu_matter"),"IC2 liquid UU producer/replicator reference uses SISE fluid");
        check(aztech.modern_industrialization.MIFluids.UU_MATTER.asFluid()==fluid("sise_ores:uu_matter"),"MI liquid UU producer/replicator reference uses SISE fluid");
        nativeMachines(server);
        industrialForegoing(server);
        for(var id:List.of("mekmm:chemical_conversion/uuitem_to_uuchemical","mekmm:oxidizing/uu_matter")){
            var recipe=server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse(id))).orElseThrow().value();
            var json=net.minecraft.world.item.crafting.Recipe.CODEC.encodeStart(ops,recipe).getOrThrow().getAsJsonObject();
            check(json.getAsJsonObject("output").get("id").getAsString().equals("sise_ores:uu_matter") && json.getAsJsonObject("output").get("amount").getAsInt()==500,"UU solid-to-chemical conversion retains original one-to-500 yield "+id);
        }
        for(var id:List.of("mekanism:hydrogen","mekanism:oxygen","mekanism:chlorine","mekanismgenerators:deuterium","mekanismgenerators:tritium","mekanism:ethene","ic2:superheated_steam","oritech:still_strange_matter"))
            check(!FluidCatalog.BY_SOURCE.containsKey(id),"different phase or processing stage protected "+id);
        return List.copyOf(checks);
    }
    private static FluidStack bucketContents(ItemStack stack){
        var handler=net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack).getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM);
        if(handler==null || handler.size()==0)throw new IllegalStateException("No native bucket capability "+BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return handler.getResource(0).toStack(Math.toIntExact(handler.getAmountAsLong(0)));
    }
    private static void industrialForegoing(MinecraftServer server){
        var pos=new net.minecraft.core.BlockPos(64,100,64);
        var unit=new com.buuz135.industrial.block.core.tile.LatexProcessingUnitTile(pos,com.buuz135.industrial.module.ModuleCore.LATEX_PROCESSING.getBlock().defaultBlockState());
        unit.setLevel(server.overworld());
        var latex=(com.hrznstudio.titanium.component.fluid.FluidTankComponent<?>)field(unit,"latex");
        var water=(com.hrznstudio.titanium.component.fluid.FluidTankComponent<?>)field(unit,"water");
        try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){
            check(latex.insert(0,FluidResource.of(fluid("sise_ores:latex")),750,tx)==750,"IF native latex validator accepts SISE latex");
            check(water.insert(0,FluidResource.of(Fluids.WATER),500,tx)==500,"IF native water tank accepts exact recipe quantity");tx.commit();
        }
        check(unit.canIncrease(),"IF latex machine can process unified input");unit.onFinish().run();
        check(latex.getFluidAmount()==0 && water.getFluidAmount()==0,"IF native latex process consumes original 750/500 mB quantities");
        var generator=new com.buuz135.industrial.block.generator.tile.BiofuelGeneratorTile(pos,com.buuz135.industrial.module.ModuleGenerator.BIOFUEL_GENERATOR.getBlock().defaultBlockState());
        generator.setLevel(server.overworld());var biofuel=(com.hrznstudio.titanium.component.fluid.FluidTankComponent<?>)field(generator,"biofuel");
        try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){
            check(biofuel.insert(0,FluidResource.of(fluid("sise_ores:biofuel")),1000,tx)==1000,"IF native generator accepts unified biofuel through transactions");tx.commit();
        }
        check(generator.consumeFuel()==4 && biofuel.getFluidAmount()==999,"IF generation preserves one mB fuel consumption and burn duration");
        for(var name:List.of("diesel","biofuel","creosote","ethanol","crude_oil","hydrogen","naphtha")){
            var group=FluidCatalog.FLUIDS.stream().filter(e->e.name().equals(name)).findFirst().orElseThrow();
            int actual=aztech.modern_industrialization.api.datamaps.FluidFuel.getEu(fluid(group.target()));
            var source=group.members().stream().filter(s->s.startsWith("modern_industrialization:")).findFirst().orElseThrow();
            int original=aztech.modern_industrialization.api.datamaps.FluidFuel.getEu(fluid(source));
            check(actual==original,"MI fluid fuel data preserves original energy per mB including non-fuels "+name+" "+original);
        }
    }
    private static Object field(Object owner,String name){
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException ignored){}catch(Exception e){throw new IllegalStateException(e);}
        throw new IllegalStateException("Missing fixture field "+name);
    }
    private static void nativeMachines(MinecraftServer server){
        var pos=new net.minecraft.core.BlockPos(64,100,64);var blocks=ic2ce.neoforge.processing.ProcessingBlocks.class;
        var classic=new ic2ce.neoforge.processing.fabrication.ClassicMatterFabricator(pos,ic2ce.neoforge.processing.ProcessingBlocks.CLASSIC_MASS_FABRICATOR.defaultBlockState());
        classic.setLevel(server.overworld());
        var energy=(ic2ce.neoforge.component.ElectricalBuffer)field(classic,"energy");energy.forceAddEnergy(energy.getCapacity());
        check(classic.attemptGeneration() && classic.outputSlot.get(0).is(item("sise_ores:uu_matter")) && classic.outputSlot.get(0).getCount()==1,"paid native IC2 classic fabrication emits one SISE solid UU");
        check(energy.getEnergy()==0 && !classic.attemptGeneration(),"native solid UU fabrication cannot repeat without paying energy");
        var liquid=new ic2ce.neoforge.processing.fabrication.LiquidMatterFabricator(pos,ic2ce.neoforge.processing.ProcessingBlocks.MATTER_GENERATOR.defaultBlockState());
        liquid.setLevel(server.overworld());energy=(ic2ce.neoforge.component.ElectricalBuffer)field(liquid,"energy");energy.forceAddEnergy(energy.getCapacity());
        var tank=(ic2ce.neoforge.fluid.Ic2FluidTank)field(liquid,"fluidTank");
        check(liquid.attemptGeneration() && tank.getFluidAmount()==1 && tank.hasExactFluid(fluid("sise_ores:uu_matter")),"paid native IC2 liquid fabrication emits one mB SISE UU");
        check(energy.getEnergy()==0 && !liquid.attemptGeneration(),"native liquid UU fabrication cannot repeat without paying energy");
        var replicator=new ic2ce.neoforge.processing.fabrication.PatternReplicator(pos,ic2ce.neoforge.processing.ProcessingBlocks.REPLICATOR.defaultBlockState());
        replicator.setLevel(server.overworld());tank=(ic2ce.neoforge.fluid.Ic2FluidTank)field(replicator,"fluidTank");
        check(tank.fillMb(ic2ce.api.fluid.Ic2FluidStack.create(aztech.modern_industrialization.MIFluids.UU_MATTER.asFluid(),1000),false)==1000,"native IC2 replicator tank accepts MI-origin canonical UU");
        try{
            var consume=replicator.getClass().getDeclaredMethod("consumeUu",double.class);consume.setAccessible(true);
            check((boolean)consume.invoke(replicator,0.001) && tank.getFluidAmount()==999,"native IC2 replication consumes exactly the prepaid one mB from unified UU");
            var converter=new ic2ce.neoforge.processing.fluid.IntegrationSteamConverter(pos,ic2ce.neoforge.processing.ProcessingBlocks.STEAM_REPRESSURIZER.defaultBlockState());
            converter.setLevel(server.overworld());var input=(ic2ce.neoforge.fluid.Ic2FluidTank)field(converter,"input");
            check(input.fillMb(ic2ce.api.fluid.Ic2FluidStack.create(fluid("sise_ores:steam"),10),false)==10,"native steam adapter accepts canonical input");
            var output=converter.getClass().getDeclaredMethod("getOutput");output.setAccessible(true);
            check((int)output.invoke(converter)==0,"identity steam adapter cannot turn ten canonical steam into sixteen");
            input.setFluidStack(null);input.fillMb(ic2ce.api.fluid.Ic2FluidStack.create(ic2ce.neoforge.registry.FluidDefinitions.SUPERHEATED_STEAM.still,10),false);
            check((int)output.invoke(converter)>0,"distinct superheated steam conversion remains enabled");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static List<String> client(){
        var result=new ArrayList<String>();var jei=local.sise.ores.compat.OresJeiPlugin.runtime;
        var helper=jei.getIngredientManager().getIngredientHelper(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK);
        for(var entry:FluidCatalog.FLUIDS){
            var stack=new FluidStack(fluid(entry.target()),1000);
            if(!helper.getResourceLocation(stack).getNamespace().equals("sise_ores"))throw new IllegalStateException("Fluid JEI mod identity "+entry.name());
            result.add("JEI fluid source is SISE Ores "+entry.name());
            if(!jei.getJeiHelpers().getIngredientVisibility().isIngredientVisible(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,stack,mezz.jei.api.ingredients.subtypes.UidContext.Ingredient))throw new IllegalStateException("Canonical fluid hidden "+entry.name());
        }
        var chemical=new ChemicalStack(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(Identifier.parse("sise_ores:uu_matter")).orElseThrow(),500);
        var chemicalType=mekanism.client.recipe_viewer.jei.MekanismJEI.TYPE_CHEMICAL;
        if(!jei.getIngredientManager().getIngredientHelper(chemicalType).getResourceLocation(chemical).getNamespace().equals("sise_ores") ||
            !jei.getJeiHelpers().getIngredientVisibility().isIngredientVisible(chemicalType,chemical,mezz.jei.api.ingredients.subtypes.UidContext.Ingredient))throw new IllegalStateException("Canonical UU chemical missing or wrong JEI mod identity");
        result.add("JEI UU chemical is visible with SISE Ores identity");
        for(var source:MaterialCatalog.BY_SOURCE.keySet())if(jei.getJeiHelpers().getIngredientVisibility().isIngredientVisible(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,new ItemStack(item(source)),mezz.jei.api.ingredients.subtypes.UidContext.Ingredient))throw new IllegalStateException("Legacy item visible "+source);
        result.add("all "+MaterialCatalog.BY_SOURCE.size()+" old material/bucket identities hidden in JEI");
        var mc=net.minecraft.client.Minecraft.getInstance();
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),true,mc.level.registryAccess());
        for(var tab:BuiltInRegistries.CREATIVE_MODE_TAB)for(var stack:tab.getDisplayItems())
            if(MaterialCatalog.BY_SOURCE.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))throw new IllegalStateException("Legacy item remains in creative tab "+BuiltInRegistries.ITEM.getKey(stack.getItem()));
        result.add("all old material and bucket entries hidden from creative tabs");
        return result;
    }
    private FluidChecks(){}
}
