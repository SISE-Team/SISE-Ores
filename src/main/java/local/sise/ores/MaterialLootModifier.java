package local.sise.ores;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.*;

/** Normalize reviewed material drops after fortune/count logic; leave special stacks intact. */
public final class MaterialLootModifier extends LootModifier {
    public static final MapCodec<MaterialLootModifier> CODEC = RecordCodecBuilder.mapCodec(
        instance -> codecStart(instance).apply(instance, MaterialLootModifier::new));

    public MaterialLootModifier(LootItemCondition[] conditions, int priority) { super(conditions, priority); }
    public static ItemStack normalize(ItemStack stack) {
        if (stack.isEmpty() || !stack.isComponentsPatchEmpty()) return stack;
        var material = MaterialCatalog.BY_SOURCE.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (material == null) return stack;
        var target = BuiltInRegistries.ITEM.getValue(Identifier.parse(material.target()));
        return target == null || target == Items.AIR ? stack : new ItemStack(target, stack.getCount());
    }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        loot.replaceAll(MaterialLootModifier::normalize);
        return loot;
    }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
