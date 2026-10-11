package local.sise.ores;

import com.google.gson.*;
import java.util.*;

/** Pure JSON transformation with per-format item fields; never traverses fluids/chemicals/components. */
public final class RecipeTransform {
    public record Result(JsonObject json, int inputs, int outputs, int formatRepairs, boolean supported) {}
    private int inputs, outputs, formatRepairs;

    public static Result transform(JsonObject original) {
        var transformer = new RecipeTransform();
        var json = original.deepCopy();
        boolean supported = transformer.apply(json);
        return new Result(json, transformer.inputs, transformer.outputs, transformer.formatRepairs, supported);
    }

    private boolean apply(JsonObject r) {
        if (!r.has("type") || !r.get("type").isJsonPrimitive()) return false;
        String type = r.get("type").getAsString();
        if (type.equals("minecraft:crafting_shaped") || type.equals("extendedcrafting:shaped_table")) {
            if (r.has("key")) r.getAsJsonObject("key").entrySet().forEach(e -> e.setValue(input(e.getValue())));
            out(r, "result");
        } else if (type.equals("minecraft:crafting_shapeless") || type.equals("extendedcrafting:shapeless_table")) {
            in(r, "ingredients"); out(r, "result");
        } else if (Set.of("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting", "modern_industrialization:forge_hammer").contains(type)) {
            in(r, "ingredient"); out(r, "result");
        } else if (type.equals("minecraft:smithing_transform")) {
            in(r, "base"); in(r, "addition"); out(r, "result");
        } else if (type.startsWith("modern_industrialization:")) {
            if (!r.has("item_inputs") && !r.has("item_outputs")) return false;
            in(r, "item_inputs"); out(r, "item_outputs");
        } else if (type.startsWith("oritech:")) {
            if (!r.has("itemInputs") && !r.has("itemResults")) return false;
            in(r, "itemInputs"); out(r, "itemResults");
        } else if (type.startsWith("mekanism:") || type.startsWith("mekmm:")) {
            repairLegacySizedInput(type, r);
            // Sized item ingredients contain 'ingredient'; chemical/fluid objects do not.
            for (var field : List.of("input", "item_input", "main_input", "extra_input")) in(r, field);
            for (var field : List.of("output", "main_output", "secondary_output", "item_output")) out(r, field);
        } else if (type.equals("enderio:sag_milling")) {
            in(r, "input"); out(r, "outputs");
        } else if (type.equals("enderio:alloy_smelting")) {
            in(r, "inputs"); out(r, "output");
        } else if (type.equals("ae2:inscriber")) {
            if (r.has("ingredients")) r.getAsJsonObject("ingredients").entrySet().forEach(e -> e.setValue(input(e.getValue())));
            out(r, "result");
        } else if (type.equals("cakesticklib:durability_use")) {
            in(r, "items"); out(r, "result");
        } else if (Set.of("ltxi:grinding", "ltxi:arc_smelting", "ltxi:electro_centrifuging").contains(type)) {
            in(r, "item_inputs"); out(r, "item_results");
        } else return false;
        return true;
    }
    private void repairLegacySizedInput(String type, JsonObject recipe) {
        // NeoEcoAE ships eight material recipes using the pre-26 SizedIngredient representation.
        // Restrict migration to simple tag/count inputs producing an explicitly unified material.
        if (!Set.of("mekanism:crushing", "mekanism:enriching").contains(type)
                || !recipe.has("input") || !recipe.get("input").isJsonObject()
                || !recipe.has("output") || !recipe.get("output").isJsonObject()) return;
        var input = recipe.getAsJsonObject("input"); var output = recipe.getAsJsonObject("output");
        if (!input.keySet().equals(Set.of("count", "tag")) || !input.get("tag").isJsonPrimitive()
                || !output.has("id") || !MaterialCatalog.BY_SOURCE.containsKey(output.get("id").getAsString())) return;
        input.addProperty("ingredient", "#" + input.remove("tag").getAsString());
        formatRepairs++;
    }
    private void in(JsonObject parent, String key) {
        if (!parent.has(key)) return;
        var value = parent.get(key);
        // A recipe's list of input slots is distinct from one ingredient's alternatives.
        if (value.isJsonArray() && Set.of("ingredients", "inputs", "item_inputs", "itemInputs", "items").contains(key)) {
            var slots = value.getAsJsonArray();
            for (int i=0;i<slots.size();i++) slots.set(i,input(slots.get(i)));
        } else parent.add(key, input(value));
    }
    private void out(JsonObject parent, String key) { if (parent.has(key)) parent.add(key, output(parent.get(key))); }

    private JsonElement input(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            var material = MaterialCatalog.BY_SOURCE.get(value.getAsString());
            if (material != null) { inputs++; return new JsonPrimitive(material.inputTag()); }
        } else if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) array.set(i, input(array.get(i)));
            // 26.1's plain list codec accepts item IDs, not a list of tags.
            // A compound ingredient expresses the same OR without adding input slots.
            if (array.asList().stream().anyMatch(e -> !e.isJsonPrimitive() || e.getAsString().startsWith("#"))) {
                var compound = new JsonObject();
                compound.addProperty("neoforge:ingredient_type", "neoforge:compound");
                compound.add("children", array);
                return compound;
            }
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            // Preserve complex ingredients, exact component requirements and their semantics.
            if (!object.has("neoforge:ingredient_type") && !object.has("components") && !object.has("type") && object.has("ingredient"))
                object.add("ingredient", input(object.get("ingredient")));
        }
        return value;
    }
    private JsonElement output(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            var material = MaterialCatalog.BY_SOURCE.get(value.getAsString());
            if (material != null) { outputs++; return new JsonPrimitive(material.target()); }
        } else if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) array.set(i, output(array.get(i)));
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("components")) return value;
            if (object.has("tag") && object.get("tag").isJsonPrimitive()
                    && object.keySet().stream().allMatch(k -> Set.of("tag", "count").contains(k))) {
                // EnderIO tag-based outputs otherwise keep choosing an upstream identity.
                var material = MaterialCatalog.BY_OUTPUT_TAG.get(object.get("tag").getAsString());
                if (material != null) { outputs++; object.remove("tag"); object.addProperty("id", material.target()); }
            } else if (object.has("id") && object.get("id").isJsonPrimitive()) {
                var material = MaterialCatalog.BY_SOURCE.get(object.get("id").getAsString());
                if (material != null) { outputs++; object.addProperty("id", material.target()); }
            } else if (object.has("item")) {
                object.add("item", output(object.get("item")));
            }
        }
        return value;
    }
    public static Set<String> remainingSources(JsonElement value) {
        var result = new TreeSet<String>();
        collect(value, result); return result;
    }
    private static void collect(JsonElement value, Set<String> result) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            var text = value.getAsString(); if (MaterialCatalog.BY_SOURCE.containsKey(text)) result.add(text);
        } else if (value.isJsonArray()) value.getAsJsonArray().forEach(e -> collect(e, result));
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> collect(e.getValue(), result));
    }
}
