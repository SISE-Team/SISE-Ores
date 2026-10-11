package local.sise.ores;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class TransformTests {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    private static JsonObject obj(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    private static void diff(JsonElement before, JsonElement after, String path) {
        if (before.equals(after)) return;
        if (before.isJsonArray() && after.isJsonObject() && after.getAsJsonObject().has("neoforge:ingredient_type")) {
            var object=after.getAsJsonObject();
            check(object.keySet().equals(Set.of("neoforge:ingredient_type","children")) && object.get("neoforge:ingredient_type").getAsString().equals("neoforge:compound"),"OR alternatives represented by compound ingredient " + path);
            diff(before,object.get("children"),path+"/children");
            return;
        }
        if (before.isJsonObject()) {
            if (before.getAsJsonObject().has("tag") && after.isJsonObject() && after.getAsJsonObject().has("id")) {
                var b = before.getAsJsonObject().deepCopy(); var a = after.getAsJsonObject().deepCopy();
                var material = MaterialCatalog.BY_OUTPUT_TAG.get(b.remove("tag").getAsString());
                check(material != null && a.remove("id").getAsString().equals(material.target()) && b.equals(a), "tag output identity only " + path);
                return;
            }
            if (before.getAsJsonObject().keySet().equals(Set.of("count", "tag"))
                    && after.isJsonObject() && after.getAsJsonObject().keySet().equals(Set.of("count", "ingredient"))) {
                check(before.getAsJsonObject().get("count").equals(after.getAsJsonObject().get("count")), "legacy input count preserved " + path);
                check(after.getAsJsonObject().get("ingredient").getAsString().equals("#" + before.getAsJsonObject().get("tag").getAsString()), "legacy tag semantics preserved " + path);
                return;
            }
            check(after.isJsonObject() && before.getAsJsonObject().keySet().equals(after.getAsJsonObject().keySet()), "keys preserved " + path);
            before.getAsJsonObject().entrySet().forEach(e -> diff(e.getValue(), after.getAsJsonObject().get(e.getKey()), path + "/" + e.getKey()));
        } else if (before.isJsonArray()) {
            check(after.isJsonArray() && before.getAsJsonArray().size() == after.getAsJsonArray().size(), "array size preserved " + path);
            for (int i = 0; i < before.getAsJsonArray().size(); i++) diff(before.getAsJsonArray().get(i), after.getAsJsonArray().get(i), path + "/" + i);
        } else {
            check(before.isJsonPrimitive() && before.getAsJsonPrimitive().isString(), "only identity strings changed " + path);
            var material = MaterialCatalog.BY_SOURCE.get(before.getAsString());
            check(material != null && (after.getAsString().equals(material.target()) || after.getAsString().equals(material.inputTag())), "reviewed identity replacement " + path);
        }
    }
    public static void main(String[] args) throws Exception {
        var catalog = MaterialCatalog.BY_SOURCE;
        check(MaterialCatalog.MATERIALS.stream().filter(m -> !m.form().equals("ores")).count() == 82, "82 existing material groups preserved");
        check(MaterialCatalog.MATERIALS.stream().filter(m -> m.form().equals("ores")).count() == 20, "20 separate ore host variants");
        var oreSmelting=obj("""
            {"type":"minecraft:smelting","ingredient":["mekanism:tin_ore","mekanism:deepslate_tin_ore"],"result":{"id":"mekanism:ingot_tin"},"cookingtime":200,"experience":0.7}
            """);
        var oreResult=RecipeTransform.transform(oreSmelting).json();
        check(oreResult.getAsJsonObject("ingredient").getAsJsonArray("children").size()==2,"two ore variants remain OR alternatives");
        diff(oreSmelting,oreResult,"ore smelting");
        var slots=obj("""
            {"type":"minecraft:crafting_shapeless","ingredients":["mekanism:tin_ore","mekanism:deepslate_tin_ore"],"result":{"id":"minecraft:stone"}}
            """);
        check(RecipeTransform.transform(slots).json().getAsJsonArray("ingredients").size()==2,"shapeless slots remain two required inputs");
        check(!MaterialCatalog.BY_OUTPUT_TAG.containsKey("c:storage_blocks/silicon"), "ambiguous silicon block tag never chooses an output");
        check(catalog.get("ae2:silicon").target().equals("sise_ores:silicon"), "ordinary silicon target");
        check(!catalog.get("modern_industrialization:silicon_block").inputTag().equals(catalog.get("oritech:silicon_block").inputTag()), "distinct silicon block inputs");
        for (String excluded : List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot", "oritech:biosteel_ingot", "ae2:charged_certus_quartz_crystal", "mekanism:dirty_dust_iron", "modern_industrialization:carbon_dust"))
            check(!catalog.containsKey(excluded), "protected " + excluded);
        var special = obj("""
            {"type":"oritech:pulverizer","time":80,"itemInputs":["oritech:biosteel_ingot"],"itemResults":[{"id":"oritech:biosteel_dust"}]}
            """);
        check(special.equals(RecipeTransform.transform(special).json()), "biosteel unchanged");
        var chance = obj("""
            {"type":"enderio:sag_milling","energy":2400,"input":"modern_industrialization:tin_ingot","outputs":[{"item":{"id":"mekanism:dust_tin","count":3},"chance":0.25}]}
            """);
        var transformed = RecipeTransform.transform(chance);
        check(transformed.outputs() == 1 && transformed.inputs() == 1, "nested chance output and input recognized");
        diff(chance, transformed.json(), "chance");
        var components = obj("""
            {"type":"minecraft:crafting_shapeless","ingredients":[{"neoforge:ingredient_type":"neoforge:components","items":"mekanism:ingot_tin","components":{"minecraft:custom_name":"Special"}}],"result":{"id":"mekanism:ingot_tin","components":{"minecraft:custom_name":"Special"}}}
            """);
        check(components.equals(RecipeTransform.transform(components).json()), "component requirements and outputs preserved");
        var chemical = obj("""
            {"type":"mekanism:metallurgic_infusing","chemical_input":{"amount":10,"tag":"mekanism:tin"},"item_input":{"count":3,"ingredient":"mekanism:dust_copper"},"output":{"count":4,"id":"mekanism:dust_bronze"},"per_tick_usage":false}
            """);
        var chemicalResult = RecipeTransform.transform(chemical);
        check(chemicalResult.json().get("chemical_input").equals(chemical.get("chemical_input")), "chemical ingredient untouched");
        diff(chemical, chemicalResult.json(), "chemical");
        var corpus = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var counts = new TreeMap<String, Integer>(); var unresolved = new TreeMap<String, Set<String>>();
        int changed = 0, repairs = 0;
        for (var entry : corpus.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            var before = entry.getValue().getAsJsonObject();
            var result = RecipeTransform.transform(before);
            repairs += result.formatRepairs();
            if (result.inputs() + result.outputs() > 0) {
                changed++; counts.merge(before.get("type").getAsString(), 1, Integer::sum);
                diff(before, result.json(), entry.getKey());
                var again = RecipeTransform.transform(result.json());
                check(again.inputs() == 0 && again.outputs() == 0 && again.formatRepairs() == 0 && again.json().equals(result.json()), "idempotent " + entry.getKey());
            }
            var left = RecipeTransform.remainingSources(result.json());
            if (!left.isEmpty()) unresolved.put(entry.getKey(), left);
        }
        check(repairs == 8, "eight NeoEcoAE legacy material recipes repaired");
        int safetyFixes = 0;
        for (var entry : corpus.entrySet()) {
            String[] path = entry.getKey().split("/", 4);
            String id = path[1] + ":" + path[3].substring(0, path[3].length() - 5);
            if (!entry.getValue().isJsonObject()) continue;
            var transformedRecipe = RecipeTransform.transform(entry.getValue().getAsJsonObject()).json();
            var originalResult = transformedRecipe.has("result") ? transformedRecipe.get("result").deepCopy() : null;
            var safety = RecipeSafety.resolve(id, transformedRecipe);
            if (safety.isEmpty()) continue;
            safetyFixes++;
            check(Objects.equals(originalResult, transformedRecipe.get("result")), "conflict fix preserves exact output " + id);
            check(RecipeSafety.resolve(id, transformedRecipe).isEmpty(), "conflict fix idempotent " + id);
            if (RecipeSafety.DUST_SPLITS.contains(id)) {
                check(transformedRecipe.get("type").getAsString().equals("minecraft:stonecutting"), "dust split is selectable " + id);
                check(transformedRecipe.has("ingredient") && !transformedRecipe.has("ingredients"), "valid stonecutting format " + id);
            }
        }
        check(safetyFixes >= 8, "dust splits and ordinary steel packing covered");
        int mineralRepairs = 0;
        for (var entry : corpus.entrySet()) {
            String[] path = entry.getKey().split("/", 4);
            String id = path[1] + ":" + path[3].substring(0, path[3].length() - 5);
            if (!entry.getValue().isJsonObject()) continue;
            var before = entry.getValue().getAsJsonObject(); var patched = before.deepCopy();
            if (MineralRepairs.repair(id, patched).isEmpty()) { check(before.equals(patched), "non-mineral recipe unchanged by repair " + id); continue; }
            mineralRepairs++;
            check(Objects.equals(before.get("output"), patched.get("output")), "mineral repair output preserved " + id);
            check(Objects.equals(before.get("chance"), patched.get("chance")), "crystal repair probability preserved " + id);
            check(Objects.equals(before.get("neoforge:conditions"), patched.get("neoforge:conditions")), "mineral conditions preserved " + id);
            check(MineralRepairs.repair(id, patched).isEmpty(), "mineral repair idempotent " + id);
        }
        check(mineralRepairs == 7, "only seven additional mineral recipes repaired");
        var report = Map.of("assertions", assertions, "changed", changed, "legacy_format_repairs", repairs, "types", counts, "unresolved", unresolved);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Assertions=" + assertions + ", changed=" + changed + ", unresolved=" + unresolved.size());
        check(unresolved.isEmpty(), "all installed recipes referencing these materials are covered; inspect report");
    }
}
