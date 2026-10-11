package local.sise.ores.compat.mixin;

import local.sise.ores.OreWorldgen;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Includes vanilla ore features, MI, Oritech, and Mekanism's configurable/retrogen targets. */
@Mixin(OreConfiguration.TargetBlockState.class)
public abstract class OreTargetMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static BlockState sise$canonicalOre(BlockState state) { return OreWorldgen.normalize(state); }
}
