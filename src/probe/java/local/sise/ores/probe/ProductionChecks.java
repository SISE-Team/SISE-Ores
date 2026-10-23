package local.sise.ores.probe;

import com.mojang.serialization.JsonOps;
import java.util.*;
import local.sise.compat.*;
import local.sise.ores.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import mekanism.api.chemical.*;

/** Actual decoded recipe quantities, batched input withdrawal and generated boiler recipe output. */
final class ProductionChecks {
    private static final List<String> checks=new ArrayList<>();
    private static void check(boolean value,String label){if(!value)throw new IllegalStateException(label);checks.add(label);}
    private static Recipe<?> recipe(MinecraftServer server,String id){return server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse(id))).orElseThrow().value();}
    private static Item item(String id){return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));}
    static List<String> verify(MinecraftServer server){
        checks.clear();var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        check(((Map<?,?>)ProductionPatches.lastReport.get("skipped")).isEmpty(),"every reviewed production source guard applied");
        check(((Map<?,?>)ProductionPatches.lastReport.get("applied")).size()==ProductionPatches.RULES.getAsJsonObject("patches").size(),"all planned production ratios installed");
        for(var rule:ProductionPatches.RULES.getAsJsonObject("patches").entrySet()){
            var expected=Recipe.CODEC.parse(ops,rule.getValue().getAsJsonObject().get("after")).getOrThrow();
            var actual=recipe(server,rule.getKey());
            check(Recipe.CODEC.encodeStart(ops,expected).getOrThrow().equals(Recipe.CODEC.encodeStart(ops,actual).getOrThrow()),"decoded native production profile matches reviewed quantities "+rule.getKey());
        }
        for(var entry:FluidCatalog.CHEMICALS){
            var source=mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(Identifier.parse(entry.preferred())).orElseThrow();
            var owned=mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(Identifier.parse(entry.target())).orElseThrow();
            check(new ChemicalStack(source,157).getChemical()==owned.value(),"native chemical production identity normalized "+entry.name());
            check(ChemicalResource.of(source).is(owned) && ChemicalResource.of(source).is(source),"chemical transport exact comparisons accept unified source "+entry.name());
            check(source.value().getAttributes().equals(owned.value().getAttributes()),"chemical fuel/coolant/radiation data preserved "+entry.name());
        }
        check(mekanism.common.registries.MekanismChemicals.STEAM.asResource().is(ResourceKey.create(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY_NAME,Identifier.parse("sise_ores:steam"))),"native boiler steam resource is SISE");
        sag(server);oritech(server);steam(server);
        return List.copyOf(checks);
    }
    private static void sag(MinecraftServer server){
        var recipe=(com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe)recipe(server,"enderio:sag_milling/raw_copper");
        check(BatchIngredient.count(recipe.input())==3,"SAG production recipe declares three raw copper per batch");
        var raw=item("minecraft:raw_copper");
        var one=new com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe.Input(new ItemStack(raw,1),com.enderio.enderio.api.components.GrindingBallData.IDENTITY);
        var three=new com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe.Input(new ItemStack(raw,3),com.enderio.enderio.api.components.GrindingBallData.IDENTITY);
        check(!recipe.matches(one,server.overworld()) && recipe.matches(three,server.overworld()),"SAG native matcher rejects an unpaid partial batch");
        var output=recipe.craft(three,server.registryAccess());
        check(output.size()==1 && output.getFirst().getItem().is(item("sise_ores:copper_dust")) && output.getFirst().getItem().getCount()==4,"SAG paid three-raw batch produces exactly four canonical dusts");
        var pos=new net.minecraft.core.BlockPos(80,100,80);
        var tile=new com.enderio.enderio.content.machines.sag_mill.SagMillBlockEntity(pos,com.enderio.enderio.init.EIOBlocks.SAG_MILL.get().defaultBlockState());tile.setLevel(server.overworld());
        var inv=tile.getInventory();
        try{
            var method=tile.getClass().getDeclaredMethod("isValidInput",int.class,net.neoforged.neoforge.transfer.item.ItemResource.class);method.setAccessible(true);
            check((boolean)method.invoke(tile,0,net.neoforged.neoforge.transfer.item.ItemResource.of(raw)),"SAG native insertion cache accepts raw copper before a full batch arrives");
            inv.setStack(com.enderio.enderio.content.machines.sag_mill.SagMillBlockEntity.INPUT,new ItemStack(raw,6));
            var factory=tile.getClass().getDeclaredMethod("createTask",net.minecraft.world.level.Level.class,three.getClass(),RecipeHolder.class);factory.setAccessible(true);
            var holder=server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Identifier.parse("enderio:sag_milling/raw_copper"))).orElseThrow();
            var task=factory.invoke(tile,server.overworld(),three,holder);
            var consume=task.getClass().getDeclaredMethod("consumeInputs",recipe.getClass());consume.setAccessible(true);consume.invoke(task,recipe);
            check(inv.getStack(com.enderio.enderio.content.machines.sag_mill.SagMillBlockEntity.INPUT).getCount()==3,"SAG actual task withdraws exactly three raw copper per batch");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void oritech(MinecraftServer server){
        var recipe=(rearth.oritech.init.recipes.OritechRecipe)recipe(server,"oritech:pulverizer/raw/copper");
        check(BatchIngredient.count(recipe.itemInputs().getFirst())==3,"Oritech raw processing declares the reference three-item batch");
        var raw=item("minecraft:raw_copper");
        var one=new rearth.oritech.init.recipes.OritechRecipeInput(List.of(new ItemStack(raw)),net.neoforged.neoforge.fluids.FluidStack.EMPTY);
        var three=new rearth.oritech.init.recipes.OritechRecipeInput(List.of(new ItemStack(raw,3)),net.neoforged.neoforge.fluids.FluidStack.EMPTY);
        check(!recipe.matches(one,server.overworld()) && recipe.matches(three,server.overworld()),"Oritech native matcher rejects a partial raw batch");
        check(recipe.itemResults().size()==1 && recipe.itemResults().getFirst().create().getCount()==4,"Oritech native output uses exactly four unified dusts");
        var tile=new rearth.oritech.block.entity.processing.PulverizerBlockEntity(new net.minecraft.core.BlockPos(80,100,80),BuiltInRegistries.BLOCK.getValue(Identifier.parse("oritech:pulverizer")).defaultBlockState());tile.setLevel(server.overworld());
        var storage=tile.inventory.getInputContainer();var resource=net.neoforged.neoforge.transfer.item.ItemResource.of(raw);
        try{
            var field=rearth.oritech.block.base.entity.MachineBlockEntity.class.getDeclaredField("currentRecipe");field.setAccessible(true);field.set(tile,recipe);
            var remove=rearth.oritech.block.base.entity.MachineBlockEntity.class.getDeclaredMethod("removeCraftingInputs",Transaction.class);remove.setAccessible(true);
            try(var tx=Transaction.openRoot()){check(storage.insert(0,resource,1,tx)==1,"Oritech input accepts raw copper one item at a time");tx.commit();}
            try(var tx=Transaction.openRoot()){check(!(boolean)remove.invoke(tile,tx),"Oritech actual withdrawal rejects an incomplete batch");}
            check(storage.getAmountAsInt(0)==1,"Oritech rejected batch preserves the original input");
            try(var tx=Transaction.openRoot()){check(storage.insert(0,resource,5,tx)==5,"Oritech input accepts the remaining paid batches");tx.commit();}
            try(var tx=Transaction.openRoot()){check((boolean)remove.invoke(tile,tx),"Oritech actual batch withdrawal succeeds");}
            check(storage.getAmountAsInt(0)==6,"Oritech transaction rollback restores the complete batch");
            try(var tx=Transaction.openRoot()){check((boolean)remove.invoke(tile,tx),"Oritech committed batch withdrawal succeeds");tx.commit();}
            check(storage.getAmountAsInt(0)==3,"Oritech actual task withdraws exactly three raw copper per batch");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void steam(MinecraftServer server){
        var pos=new net.minecraft.core.BlockPos(80,100,80);
        var casing=new mekanism.common.tile.multiblock.TileEntityBoilerCasing(pos,mekanism.common.registries.MekanismBlocks.BOILER_CASING.get().defaultBlockState());casing.setLevel(server.overworld());
        var boiler=new mekanism.common.content.boiler.BoilerMultiblockData(casing);boiler.setFormedForce(true);boiler.inventoryID=UUID.randomUUID();boiler.setWaterVolume(1);boiler.setSteamVolume(1);boiler.superheatingElements=8;
        try{
            var heatField=boiler.getClass().getDeclaredField("heatCapacitor");heatField.setAccessible(true);
            var heat=(mekanism.common.capabilities.heat.VariableHeatCapacitor)heatField.get(boiler);heat.setHeat(heat.getHeatCapacity()*1000,null);
            try(var tx=Transaction.openRoot()){check(boiler.waterTank.insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(net.minecraft.world.level.material.Fluids.WATER),1000,tx,mekanism.api.AutomationType.INTERNAL)==1000,"Mekanism actual boiler accepts the water feed");tx.commit();}
            boiler.tick(server.overworld());
            check(boiler.lastBoilRate>0 && boiler.steamTank.amountAsInt()==1000-boiler.waterTank.amountAsInt(),"Mekanism actual thermal boiler produces one canonical steam per water");
            check(boiler.steamTank.resource().is(ResourceKey.create(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY_NAME,Identifier.parse("sise_ores:steam"))),"Mekanism actual thermal boiler tank contains SISE steam");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        var heater=new aztech.modern_industrialization.machines.components.SteamHeaterComponent(1500,160,8);
        var water=aztech.modern_industrialization.inventory.ConfigurableFluidStack.lockedInputSlot(1000,net.minecraft.world.level.material.Fluids.WATER);
        var out=aztech.modern_industrialization.inventory.ConfigurableFluidStack.lockedOutputSlot(1000,aztech.modern_industrialization.MIFluids.STEAM.asFluid());
        water.setAmount(1000);heater.increaseTemperature(1500);
        heater.tick(List.of(water),List.of(out));
        check(out.getAmount()>0 && 1000-water.getAmount()==out.getAmount(),"MI actual boiler consumes one water per one canonical steam");
        var condenser=new ic2ce.neoforge.processing.fluid.SteamCondenser(pos,ic2ce.neoforge.processing.ProcessingBlocks.CONDENSER.defaultBlockState());condenser.setLevel(server.overworld());
        condenser.getInputTank().fillMb(ic2ce.api.fluid.Ic2FluidStack.create(aztech.modern_industrialization.MIFluids.STEAM.asFluid(),100),false);
        try{
            var tick=condenser.getClass().getDeclaredMethod("updateEntityServer");tick.setAccessible(true);
            for(int i=0;i<100;i++)tick.invoke(condenser);
            check(condenser.getInputTank().isEmpty() && condenser.getOutputTank().getFluidAmount()==100,"IC2 actual condensation returns exactly 100 water from 100 unified steam");
            var hot=new ic2ce.neoforge.processing.fluid.SteamCondenser(pos,ic2ce.neoforge.processing.ProcessingBlocks.CONDENSER.defaultBlockState());hot.setLevel(server.overworld());
            hot.getInputTank().fillMb(ic2ce.api.fluid.Ic2FluidStack.create(ic2ce.neoforge.registry.FluidDefinitions.SUPERHEATED_STEAM.still,100),false);
            for(int i=0;i<100;i++)tick.invoke(hot);
            check(hot.getInputTank().isEmpty() && hot.getOutputTank().getFluidAmount()==100,"IC2 distinct superheated steam also condenses at the same water-equivalent unit");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        ic2Boiler(server,pos);
    }
    private static final class HeatFeed extends net.minecraft.world.level.block.entity.BlockEntity implements ic2ce.api.energy.tile.IHeatSource {
        HeatFeed(net.minecraft.core.BlockPos pos){super(net.minecraft.world.level.block.entity.BlockEntityType.FURNACE,pos,net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());}
        @Override public int maxrequestHeatTick(net.minecraft.core.Direction direction){return 1200;}
        @Override public int requestHeat(net.minecraft.core.Direction direction,int amount){return Math.min(amount,1200);}
        @Override public int drawHeat(net.minecraft.core.Direction direction,int amount,boolean simulate){return Math.min(amount,1200);}
    }
    private static void ic2Boiler(MinecraftServer server,net.minecraft.core.BlockPos pos){
        var level=server.overworld();var source=pos.below();level.setBlockAndUpdate(source,net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());level.setBlockEntity(new HeatFeed(source));
        try{
            var type=ic2ce.neoforge.processing.fluid.PressureBoiler.class;
            var heat=type.getDeclaredField("systemHeat");heat.setAccessible(true);var input=type.getDeclaredField("inputMB");input.setAccessible(true);var pressure=type.getDeclaredField("pressure");pressure.setAccessible(true);
            var buffer=type.getDeclaredField("steamBuffer");buffer.setAccessible(true);var tick=type.getDeclaredMethod("updateEntityServer");tick.setAccessible(true);
            for(boolean hot:List.of(false,true)){
                var boiler=new ic2ce.neoforge.processing.fluid.PressureBoiler(pos,ic2ce.neoforge.processing.ProcessingBlocks.STEAM_GENERATOR.defaultBlockState());boiler.setLevel(level);
                heat.setFloat(boiler,hot?374F:100F);pressure.setInt(boiler,hot?220:0);input.setInt(boiler,1);
                boiler.waterTank.fillMb(ic2ce.api.fluid.Ic2FluidStack.create(net.minecraft.world.level.material.Fluids.WATER,100),false);tick.invoke(boiler);
                var result=(ic2ce.neoforge.fluid.Ic2FluidTank)buffer.get(boiler);
                check(result.getFluidAmount()>0 && result.getFluidAmount()==100-boiler.waterTank.getFluidAmount(),"IC2 actual pressure boiler uses one water-equivalent unit "+(hot?"superheated":"ordinary"));
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}finally{level.removeBlock(source,false);}
    }
    static List<String> client(){
        var result=new ArrayList<String>();var jei=local.sise.ores.compat.OresJeiPlugin.runtime;
        var type=mekanism.client.recipe_viewer.jei.MekanismJEI.TYPE_CHEMICAL;var helper=jei.getIngredientManager().getIngredientHelper(type);
        for(var entry:FluidCatalog.CHEMICALS){var stack=new ChemicalStack(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(Identifier.parse(entry.target())).orElseThrow(),1);
            if(!helper.getDisplayModId(stack).equals("sise_ores") || !jei.getJeiHelpers().getIngredientVisibility().isIngredientVisible(type,stack,mezz.jei.api.ingredients.subtypes.UidContext.Ingredient))throw new IllegalStateException("Chemical display source/visibility "+entry.name());
            result.add("JEI chemical source is SISE Ores "+entry.name());}
        for(var recipe:mekanism.client.recipe_viewer.recipe.BoilerRecipeViewerRecipe.getBoilerRecipes()){
            if(!recipe.steam().typeHolder().is(ResourceKey.create(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY_NAME,Identifier.parse("sise_ores:steam"))) || recipe.steam().amount()!=1)throw new IllegalStateException("Boiler generated recipe still produces old steam or wrong quantity");
            result.add("generated thermal boiler recipe shows one SISE steam "+recipe.id());}
        return result;
    }
    private ProductionChecks(){}
}
