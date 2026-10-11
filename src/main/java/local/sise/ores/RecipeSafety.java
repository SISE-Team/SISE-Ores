package local.sise.ores;

import com.google.gson.*;
import java.util.*;

/** Explicit conflict resolutions verified against this instance's live recipe registry. */
public final class RecipeSafety {
    public static final Set<String> DUST_SPLITS = Set.of(
        "oritech:crafting/small_iron_dust_from_unpacking",
        "oritech:crafting/small_gold_dust_from_unpacking",
        "oritech:crafting/small_copper_dust_from_unpacking",
        "oritech:crafting/small_nickel_dust_from_unpacking",
        "oritech:crafting/small_platinum_dust_from_unpacking",
        "oritech:uranium_dustblockinv", "oritech:plutonium_dustblockinv");

    public static boolean manualOnly(String id) {
        return id.startsWith("sise_ores:convert/") || DUST_SPLITS.contains(id);
    }

    public static String resolve(String id, JsonObject recipe) {
        if (!recipe.has("type") || !recipe.get("type").isJsonPrimitive()) return "";
        // The public silicon block tag contains two distinct 9:1 storage families.
        if (id.equals("oritech:silicon_blockblockinv") && recipe.has("ingredients")) {
            var inputs = recipe.getAsJsonArray("ingredients");
            if (inputs.size() == 1 && inputs.get(0).equals(new JsonPrimitive("#c:storage_blocks/silicon"))) {
                inputs.set(0, new JsonPrimitive("#sise_ores:equivalents/storage_blocks/ordinary_silicon"));
                return "ordinary silicon unpacking excludes ingot blocks";
            }
        }
        if (id.equals("modern_industrialization:materials/silicon/unpacker/ingot") && recipe.has("item_inputs")) {
            var inputs = recipe.getAsJsonArray("item_inputs");
            if (inputs.size() == 1 && inputs.get(0).getAsJsonObject().get("ingredient").equals(new JsonPrimitive("#c:storage_blocks/silicon"))) {
                inputs.get(0).getAsJsonObject().addProperty("ingredient", "#sise_ores:equivalents/storage_blocks/silicon_metal");
                return "silicon ingot unpacking excludes ordinary silicon blocks";
            }
        }
        if (DUST_SPLITS.contains(id) && recipe.get("type").getAsString().equals("minecraft:crafting_shapeless")
                && recipe.getAsJsonArray("ingredients").size() == 1) {
            var ingredient = recipe.remove("ingredients").getAsJsonArray().get(0);
            recipe.remove("category");
            recipe.addProperty("type", "minecraft:stonecutting");
            recipe.add("ingredient", ingredient);
            return "selectable dust subdivision; original output/count retained";
        }
        if (recipe.get("type").getAsString().equals("minecraft:crafting_shaped")
                && recipe.has("result") && recipe.get("result").isJsonObject()
                && recipe.getAsJsonObject("result").has("id")
                && recipe.getAsJsonObject("result").get("id").getAsString().equals("sise_ores:steel_block")) {
            boolean changed = false;
            for (var entry : recipe.getAsJsonObject("key").entrySet()) {
                if (entry.getValue().equals(new JsonPrimitive("#c:ingots/steel"))) {
                    entry.setValue(new JsonPrimitive("#sise_ores:equivalents/ingots/steel"));
                    changed = true;
                }
            }
            if (changed) return "ordinary steel packing excludes biosteel";
        }
        return "";
    }
    private RecipeSafety() {}
}
