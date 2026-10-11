package local.sise.ores;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import java.util.*;

@Mod(SiseOres.ID)
public final class SiseOres {
    public static final String ID = "sise_ores";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>> LOOT = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ID);
    public static final Map<String, DeferredItem<?>> MATERIAL_ITEMS = new LinkedHashMap<>();
    public static final Map<String, DeferredBlock<Block>> MATERIAL_BLOCKS = new LinkedHashMap<>();
    static {
        LOOT.register("materials", () -> MaterialLootModifier.CODEC);
        for (var material : MaterialCatalog.MATERIALS) {
            if (!material.owned()) continue;
            if (material.form().equals("ores")) {
                java.util.function.Supplier<Block> source = () -> Objects.requireNonNull(BuiltInRegistries.BLOCK.getValue(Identifier.parse(material.preferred())));
                DeferredBlock<Block> block = BLOCKS.registerBlock(material.name(), props -> new UnifiedOreBlock(props, source.get()),
                    () -> BlockBehaviour.Properties.ofFullCopy(source.get())
                        .overrideLootTable(Optional.of(ResourceKey.create(Registries.LOOT_TABLE, id("blocks/" + material.name()))))
                        .overrideDescription("block.sise_ores." + material.name()));
                MATERIAL_BLOCKS.put(material.name(), block);
                MATERIAL_ITEMS.put(material.name(), ITEMS.registerSimpleBlockItem(block));
            } else if (material.form().equals("storage_blocks")) {
                var block = BLOCKS.registerSimpleBlock(material.name(), props -> props.strength(5, 6).sound(SoundType.METAL).requiresCorrectToolForDrops());
                MATERIAL_BLOCKS.put(material.name(), block);
                MATERIAL_ITEMS.put(material.name(), ITEMS.registerSimpleBlockItem(block));
            } else {
                MATERIAL_ITEMS.put(material.name(), ITEMS.registerSimpleItem(material.name()));
            }
        }
        TABS.register("materials", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.sise_ores"))
            .icon(() -> MATERIAL_ITEMS.get("tin_ingot").get().getDefaultInstance())
            .displayItems((parameters, output) -> MATERIAL_ITEMS.values().forEach(output::accept)).build());
    }
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(ID, name); }
    public SiseOres(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TABS.register(bus); LOOT.register(bus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RecipeUnifier::onRecipes);
    }
}
