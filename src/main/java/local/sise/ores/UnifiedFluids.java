package local.sise.ores;

import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.*;
import mekanism.api.chemical.*;

/** SISE-owned source/flow identities and buckets; physical/render attributes use the preferred type. */
public final class UnifiedFluids {
    public static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,SiseOres.ID);
    public static final DeferredRegister<Chemical> CHEMICALS=DeferredRegister.create(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY_NAME,SiseOres.ID);
    public static final Map<String,DeferredHolder<Fluid,? extends FlowingFluid>> SOURCES=new LinkedHashMap<>(),FLOWING=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<BucketItem>> BUCKETS=new LinkedHashMap<>();
    public static final Map<String,DeferredBlock<LiquidBlock>> BLOCKS=new LinkedHashMap<>();
    public static void registerEntries() {
        for(var entry:FluidCatalog.FLUIDS) {
            var name=entry.name();
            java.util.function.Supplier<BaseFlowingFluid.Properties> props=()->new BaseFlowingFluid.Properties(
                ()->BuiltInRegistries.FLUID.getValue(Identifier.parse(entry.preferred())).getFluidType(),
                ()->SOURCES.get(name).get(),()->FLOWING.get(name).get()).bucket(()->BUCKETS.get(name).get()).block(()->BLOCKS.get(name).get());
            SOURCES.put(name,FLUIDS.register(name,()->new BaseFlowingFluid.Source(props.get())));
            FLOWING.put(name,FLUIDS.register("flowing_"+name,()->new BaseFlowingFluid.Flowing(props.get())));
            BLOCKS.put(name,SiseOres.BLOCKS.registerBlock(name, p->new FluidBlock(SOURCES.get(name).get(),p),
                ()->net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()));
            BUCKETS.put(name,SiseOres.ITEMS.registerItem(name+"_bucket",p->new BucketItem(SOURCES.get(name).get(),p.craftRemainder(Items.BUCKET).stacksTo(1))));
        }
        for(var entry:FluidCatalog.CHEMICALS)CHEMICALS.register(entry.name(),()->{
            var source=mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getValue(Identifier.parse(entry.preferred()));
            return new Chemical(ChemicalBuilder.builder(source.getIcon()).tint(source.getTint()));
        });
    }
    private static final class FluidBlock extends LiquidBlock {
        FluidBlock(FlowingFluid fluid,net.minecraft.world.level.block.state.BlockBehaviour.Properties properties){super(fluid,properties);}
    }
    private UnifiedFluids(){}
}
