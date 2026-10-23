package local.sise.ores;

import com.google.gson.*;
import java.util.*;

/** Typed identity transformations; component payloads, quantities and processing settings are retained. */
public final class RecipeTransform {
    public record Result(JsonObject json, int inputs, int outputs, boolean supported) {}
    private int inputs, outputs;

    public static Result transform(JsonObject original) {
        var transformer = new RecipeTransform();
        var json = original.deepCopy();
        boolean supported = transformer.apply(json);
        int fluids=FluidRecipeTransform.apply(json);
        return new Result(json, transformer.inputs+fluids, transformer.outputs, supported || fluids>0);
    }

    private boolean apply(JsonObject r) {
        if (!r.has("type") || !r.get("type").isJsonPrimitive()) return false;
        String type = r.get("type").getAsString();
        if (type.equals("ic2:shaped")) {
            if (r.has("key")) r.getAsJsonObject("key").entrySet().forEach(e -> e.setValue(ic2Input(e.getValue())));
            out(r, "result");
        } else if (type.equals("ic2:shapeless")) {
            if (r.has("ingredients")) {
                var slots = r.getAsJsonArray("ingredients");
                for (int i=0;i<slots.size();i++) slots.set(i, ic2Input(slots.get(i)));
            }
            out(r, "result");
        } else if (Set.of("ic2:blast_furnace", "ic2:block_cutter", "ic2:centrifuge", "ic2:compressor",
                "ic2:extractor", "ic2:macerator", "ic2:metal_former_cutting", "ic2:metal_former_extruding",
                "ic2:metal_former_rolling", "ic2:ore_washer", "ic2:matter_fabricator").contains(type)) {
            if (r.has("ingredient")) r.add("ingredient", ic2Input(r.get("ingredient")));
            out(r, "result");
        } else if (type.equals("ic2:canner_bottle")) {
            for (var field : List.of("container_ingredient", "fill_ingredient"))
                if (r.has(field)) r.add(field, ic2Input(r.get(field)));
            out(r, "result");
        } else if (type.equals("ic2:canner_enrich")) {
            if (r.has("additive_ingredient")) r.add("additive_ingredient", ic2Input(r.get("additive_ingredient")));
            // input_ingredient and result are fluid stacks, not physical material identities.
        } else if (type.equals("minecraft:crafting_shaped") || type.equals("extendedcrafting:shaped_table") || type.equals("mekanism:mek_data")) {
            if (r.has("key")) r.getAsJsonObject("key").entrySet().forEach(e -> e.setValue(input(e.getValue())));
            out(r, "result");
        } else if (type.equals("minecraft:crafting_shapeless") || type.equals("extendedcrafting:shapeless_table") || type.equals("tofucraft:tofu_pot_shapeless")) {
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
        } else if (type.equals("ae2cs:crystal_pulverizer_recipe_serializer")) {
            in(r, "input"); out(r, "result");
        } else if (type.equals("cakesticklib:durability_use")) {
            in(r, "items"); out(r, "result");
        } else if (type.equals("powah:energizing")) {
            in(r, "ingredients"); out(r, "result");
        } else if (type.equals("productivemetalworks:item_casting") || type.equals("productivemetalworks:block_casting")) {
            // The casting mold and molten-fluid amount retain their native meaning.
            out(r, "result");
        } else if (Set.of("ltxi:grinding", "ltxi:arc_smelting", "ltxi:electro_centrifuging", "ltxi:air_scrubbing", "ltxi:pressing").contains(type)) {
            in(r, "item_inputs"); out(r, "item_results");
        } else return false;
        return true;
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

    /** IC2CE's legacy inputs retain counts and counted OR alternatives in their native codec. */
    private JsonElement ic2Input(JsonElement value) {
        if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i=0;i<array.size();i++) array.set(i, ic2Input(array.get(i)));
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("components") || object.has("nbt") || object.has("data") || object.has("fluid")
                    || object.has("neoforge:ingredient_type") || object.has("type")) return value;
            if (object.has("any")) object.add("any", ic2Input(object.get("any")));
            else if (object.has("ingredient")) object.add("ingredient", input(object.get("ingredient")));
            else if (object.has("item") && object.get("item").isJsonPrimitive()
                    && object.keySet().stream().allMatch(k -> Set.of("item", "count").contains(k))) {
                var material = MaterialCatalog.BY_SOURCE.get(object.get("item").getAsString());
                if (material != null) {
                    inputs++; object.remove("item"); object.addProperty("tag", material.inputTag().substring(1));
                }
            }
        } else return input(value);
        return value;
    }

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
            if (object.has("components") || object.has("nbt") || object.has("data")) return value;
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
        else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            // Existing SISE Compat manual conversions deliberately require the exact pristine old item.
            if (object.has("neoforge:ingredient_type") && object.get("neoforge:ingredient_type").getAsString().equals("sise_compat:pristine")) return;
            // Registry conditions decide whether an upstream recipe exists. They are
            // not ingredient identities and must continue to test that upstream item.
            object.entrySet().stream().filter(e -> !e.getKey().equals("neoforge:conditions"))
                .forEach(e -> collect(e.getValue(), result));
        }
    }
}
