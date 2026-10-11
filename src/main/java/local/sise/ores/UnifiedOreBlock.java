package local.sise.ores;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The canonical block keeps the priority source's mining properties and experience. */
public final class UnifiedOreBlock extends Block {
    private final Block source;
    public UnifiedOreBlock(Properties properties, Block source) { super(properties); this.source = source; }
    @Override public int getExpDrop(BlockState state, LevelAccessor level, BlockPos pos, BlockEntity entity, Entity breaker, ItemStack tool) {
        return source.getExpDrop(source.defaultBlockState(), level, pos, entity, breaker, tool);
    }
}
