package local.sise.ores.compat.mixin;

import java.util.*;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RarityCore 2601.14.2 retains live ConcurrentHashMaps in its packet record.
 * Async rarity calculation can change map.size() between length encoding and iteration,
 * producing a truncated payload. Snapshot once at construction without changing the wire format.
 */
@Pseudo
@Mixin(targets = "org.yanbwe.raritycore.network.RaritySyncPayload", remap = false)
public abstract class RaritySyncSnapshotMixin {
    @Shadow @Final @Mutable private Map<Identifier, Integer> itemRarityData;
    @Shadow @Final @Mutable private Map<Identifier, Integer> autoRarityData;
    @Shadow @Final @Mutable private List<?> tagRules;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void siseOres$snapshot(Map<Identifier, Integer> items, Map<Identifier, Integer> automatic,
                                    List<?> rules, CallbackInfo callback) {
        itemRarityData = Map.copyOf(items);
        autoRarityData = Map.copyOf(automatic);
        tagRules = List.copyOf(rules);
    }
}
