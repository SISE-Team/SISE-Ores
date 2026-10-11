package local.sise.ores.compat.mixin;

import local.sise.ores.OreWorldgen;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Covers structures and custom generators writing blocks through the generation region. */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenOreMixin {
    @ModifyVariable(method = "setBlock", at = @At("HEAD"), argsOnly = true)
    private BlockState sise$canonicalOre(BlockState state) { return OreWorldgen.normalize(state); }
}
