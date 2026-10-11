package local.sise.ores;

import com.google.gson.*;
import java.util.*;

/** Only the seven reviewed mineral-processing failures, never unrelated pack recipes. */
public final class MineralRepairs {
    public static final Set<String> CRUSHING = Set.of(
        "neoecoae:crushing/aluminum_alloy_dust", "neoecoae:crushing/black_tungsten_alloy_dust",
        "neoecoae:crushing/energized_crystal_dust", "neoecoae:crushing/energized_flux_crystal_dust");
    public static final Set<String> CRYSTAL_FIXING = Set.of(
        "neoecoae:crystal_fixer/chipped_budding_energized_crystal",
        "neoecoae:crystal_fixer/damaged_budding_energized_crystal",
        "neoecoae:crystal_fixer/flawed_budding_energized_crystal");

    public static String repair(String id, JsonObject recipe) {
        if (CRUSHING.contains(id) && recipe.has("input") && recipe.get("input").isJsonObject()) {
            var input = recipe.getAsJsonObject("input");
            if (input.keySet().equals(Set.of("count", "tag"))) {
                input.addProperty("ingredient", "#" + input.remove("tag").getAsString());
                return "migrate mineral crushing tag/count; preserve output and quantity";
            }
        }
        if (CRYSTAL_FIXING.contains(id) && recipe.has("fuel") && recipe.get("fuel").isJsonObject()) {
            var fuel = recipe.getAsJsonObject("fuel");
            if (fuel.has("ingredient") && fuel.get("ingredient").isJsonObject()) {
                var ingredient = fuel.getAsJsonObject("ingredient");
                if (ingredient.keySet().equals(Set.of("tag"))) {
                    fuel.addProperty("ingredient", "#" + ingredient.get("tag").getAsString());
                    return "migrate crystal fuel ingredient; preserve crystal states and chance";
                }
            }
        }
        return "";
    }
    private MineralRepairs() {}
}
