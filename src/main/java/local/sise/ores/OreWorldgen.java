package local.sise.ores;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Replace only reviewed ore identities, only at world-generation entry points. */
public final class OreWorldgen {
    public static final Map<String, MaterialCatalog.Material> SOURCES;
    static {
        var sources = new HashMap<String, MaterialCatalog.Material>();
        for (var m : MaterialCatalog.MATERIALS) if (m.form().equals("ores"))
            for (var source : m.members()) sources.put(source, m);
        SOURCES = Map.copyOf(sources);
    }
    public static BlockState normalize(BlockState state) {
        var target = Resolved.BLOCKS.get(state.getBlock());
        return target == null ? state : target.withPropertiesOf(state);
    }
    // First use occurs after block registration. Avoid registry lookups/string allocations per placement.
    private static final class Resolved {
        static final Map<Block, Block> BLOCKS = resolve();
        private static Map<Block, Block> resolve() {
            var result = new IdentityHashMap<Block, Block>();
            SOURCES.forEach((id, material) -> {
                var source = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
                if (source == null || source == Blocks.AIR) throw new IllegalStateException("Missing reviewed ore " + id);
                result.put(source, SiseOres.MATERIAL_BLOCKS.get(material.name()).get());
            });
            return Collections.unmodifiableMap(result);
        }
    }
    private OreWorldgen() {}
}
