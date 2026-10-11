package local.sise.ores;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.fml.loading.FMLPaths;

public final class RecipeUnifier {
    public static volatile Map<String, Object> lastReport = Map.of();
    public static void onRecipes(ModifyRecipeJsonsEvent event) {
        int inputs = 0, outputs = 0, formatRepairs = 0;
        var changed = new TreeMap<String, String>();
        var unresolved = new TreeMap<String, Set<String>>();
        var safetyFixes = new TreeMap<String, String>();
        var mineralFixes = new TreeMap<String, String>();
        for (var entry : event.getRecipeJsons().entrySet()) {
            // Legacy conversions must retain the exact old item, excluding the standard item.
            if (entry.getKey().getNamespace().equals(SiseOres.ID) || !entry.getValue().isJsonObject()) continue;
            var source = entry.getValue().getAsJsonObject().deepCopy();
            String repair = MineralRepairs.repair(entry.getKey().toString(), source);
            if (!repair.isEmpty()) mineralFixes.put(entry.getKey().toString(), repair);
            var result = RecipeTransform.transform(source);
            String safety = RecipeSafety.resolve(entry.getKey().toString(), result.json());
            if (!safety.isEmpty()) safetyFixes.put(entry.getKey().toString(), safety);
            if (result.inputs() + result.outputs() + result.formatRepairs() > 0 || !safety.isEmpty() || !repair.isEmpty()) {
                entry.setValue(result.json()); inputs += result.inputs(); outputs += result.outputs();
                formatRepairs += result.formatRepairs();
                changed.put(entry.getKey().toString(), result.json().get("type").getAsString());
            }
            var left = RecipeTransform.remainingSources(result.json());
            if (!left.isEmpty()) unresolved.put(entry.getKey().toString(), left);
        }
        lastReport = Map.of("material_groups", MaterialCatalog.MATERIALS.size(), "changed_recipes", changed.size(),
            "inputs_replaced", inputs, "outputs_replaced", outputs, "legacy_format_repairs", formatRepairs, "recipes", changed, "unresolved", unresolved, "conflict_fixes", safetyFixes, "mineral_format_repairs", mineralFixes);
        try {
            var path = FMLPaths.GAMEDIR.get().resolve("logs/sise-ores-unification.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(lastReport));
        } catch (Exception e) { LogUtils.getLogger().warn("Could not save SISE Ores report", e); }
        LogUtils.getLogger().info("SISE Ores: {} recipes unified, {} inputs, {} outputs, {} entries need review", changed.size(), inputs, outputs, unresolved.size());
    }
    private RecipeUnifier() {}
}
