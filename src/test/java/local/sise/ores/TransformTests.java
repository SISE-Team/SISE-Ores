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
            if (before.getAsJsonObject().has("item") && after.isJsonObject() && after.getAsJsonObject().has("tag")) {
                var b = before.getAsJsonObject().deepCopy(); var a = after.getAsJsonObject().deepCopy();
                var material = MaterialCatalog.BY_SOURCE.get(b.remove("item").getAsString());
                check(material != null && a.remove("tag").getAsString().equals(material.inputTag().substring(1)) && b.equals(a), "IC2 counted input identity only " + path);
                return;
            }
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
            var fluid=FluidCatalog.BY_SOURCE.get(before.getAsString());
            var chemical=FluidCatalog.CHEMICAL_BY_SOURCE.get(before.getAsString());
            check((material != null && (after.getAsString().equals(material.target()) || after.getAsString().equals(material.inputTag())))
                || (fluid!=null && after.getAsString().equals(fluid.target())) || (chemical!=null && after.getAsString().equals(chemical.target())), "reviewed identity replacement " + path);
        }
    }
    public static void main(String[] args) throws Exception {
        var catalog = MaterialCatalog.BY_SOURCE;
        check(MaterialCatalog.MATERIALS.stream().filter(m -> !Set.of("ores","equipment").contains(m.form())).count() == 158, "158 reviewed material groups including UU, scrap stages, wafers and fluid buckets");
        check(MaterialCatalog.MATERIALS.stream().filter(m -> m.form().equals("equipment")).count()==14,"fourteen same-recipe equipment groups retain native priority items");
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
        for (String excluded : List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot", "oritech:biosteel_ingot", "ae2:charged_certus_quartz_crystal", "mekanism:dirty_dust_iron"))
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
        var ic2 = obj("""
            {"type":"ic2:compressor","ingredient":{"item":"ic2:raw_tin","count":9},"result":{"item":"ic2:raw_tin_block"},"duration":400,"weighted":false}
            """);
        var ic2Result = RecipeTransform.transform(ic2);
        check(ic2Result.inputs()==1 && ic2Result.outputs()==1, "IC2 legacy counted input and output recognized");
        diff(ic2, ic2Result.json(), "IC2 compressor");
        var alternatives = obj("""
            {"type":"ic2:macerator","ingredient":{"any":[{"item":"ic2:tin_ingot","count":2},{"item":"modern_industrialization:tin_ingot","count":3}],"count":4},"result":[{"item":"ic2:tin_dust","count":2,"weight":0.25}]}
            """);
        var alternativeResult=RecipeTransform.transform(alternatives);
        check(alternativeResult.json().getAsJsonObject("ingredient").getAsJsonArray("any").size()==2,"IC2 counted OR alternatives retain their own codec");
        diff(alternatives,alternativeResult.json(),"IC2 alternatives");
        for (String field : List.of("components", "nbt", "data")) {
            var exact=obj("""
                {"type":"ic2:macerator","ingredient":{"item":"ic2:tin_ingot","count":2},"result":{"item":"ic2:tin_dust","count":2}}
                """);
            exact.getAsJsonObject("ingredient").add(field,new JsonObject());
            exact.getAsJsonObject("result").add(field,new JsonObject());
            check(RecipeTransform.transform(exact).json().equals(exact),"IC2 exact stack data preserved: "+field);
        }
        for (String excluded : List.of("ic2:uranium", "ic2:uranium_235", "ic2:uranium_238", "ic2:plutonium", "ic2:crushed_tin", "ic2:purified_tin", "ic2:iridium_shard", "ic2:refined_iron_ingot", "mekanism:block_salt", "oritech:uranium"))
            check(!catalog.containsKey(excluded),"distinct stage/property protected "+excluded);
        check(!catalog.get("cakesticklib:carbon_dust").target().equals(catalog.get("ic2:coal_dust").target()),"carbon and coal stay distinct despite overlapping public tags");
        check(!catalog.get("ic2:small_tin_dust").target().equals(catalog.get("ic2:tin_dust").target()),"one-ninth powder remains separate from whole powder");
        check(catalog.get("ic2:iron_plate").target().equals("sise_ores:iron_plate"),"IC2 ordinary iron plate unified");
        check(catalog.get("industrialforegoing:gold_gear").target().equals("sise_ores:gold_gear"),"duplicate gold gear unified");
        check(catalog.get("industrialforegoing:plastic").target().equals(catalog.get("oritech:plastic_sheet").target()),"ordinary plastic sheets unified across tag aliases");
        check(catalog.get("ic2:carbon_plate").target().equals(catalog.get("cakesticklib:carbon_plate").target()),"carbon fiber composites unified");
        for (String excluded : List.of("ic2:dense_iron_plate", "ic2:dense_gold_plate", "modern_industrialization:iron_large_plate",
                "modern_industrialization:copper_curved_plate", "modern_industrialization:carbon_plate", "ae2:printed_silicon",
                "modern_industrialization:silicon_n_doped_plate", "modern_industrialization:silicon_p_doped_plate",
                "ic2:rubber_sheet", "modern_industrialization:rubber_sheet", "oritech:reinforced_carbon_sheet", "mekanism:hdpe_sheet"))
            check(!catalog.containsKey(excluded),"distinct manufactured material stage protected "+excluded);
        var pressing=obj("""
            {"type":"ltxi:pressing","item_inputs":[{"ingredient":"modern_industrialization:titanium_ingot","count":4}],"item_results":[{"id":"ltxi:titanium_gear","count":1}],"mode":"ltxi:gear_pressing"}
            """);
        var pressed=RecipeTransform.transform(pressing);
        check(pressed.inputs()==1 && pressed.outputs()==1 && pressed.json().get("mode").equals(pressing.get("mode")),"pressing preserves quantity and machine mode");
        diff(pressing,pressed.json(),"manufactured parts pressing");
        var conditional=obj("""
            {"type":"minecraft:crafting_shaped","neoforge:conditions":[{"type":"neoforge:registered","value":"industrialforegoing:iron_gear"}],"pattern":["I"],"key":{"I":"minecraft:iron_ingot"},"result":{"id":"industrialforegoing:iron_gear"}}
            """);
        var conditionalResult=RecipeTransform.transform(conditional).json();
        check(conditionalResult.get("neoforge:conditions").equals(conditional.get("neoforge:conditions")) && RecipeTransform.remainingSources(conditionalResult).isEmpty(),"upstream registry conditions preserved separately from material inputs/outputs");
        var corpus = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var counts = new TreeMap<String, Integer>(); var unresolved = new TreeMap<String, Set<String>>();
        int changed = 0;
        for (var entry : corpus.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            var before = entry.getValue().getAsJsonObject();
            var result = RecipeTransform.transform(before);
            if (result.inputs() + result.outputs() > 0) {
                changed++; counts.merge(before.get("type").getAsString(), 1, Integer::sum);
                diff(before, result.json(), entry.getKey());
                var again = RecipeTransform.transform(result.json());
                check(again.inputs() == 0 && again.outputs() == 0 && again.json().equals(result.json()), "idempotent " + entry.getKey());
            }
            var left = RecipeTransform.remainingSources(result.json());
            if (!left.isEmpty()) unresolved.put(entry.getKey(), left);
        }
        var report = Map.of("assertions", assertions, "changed", changed, "types", counts, "unresolved", unresolved);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Assertions=" + assertions + ", changed=" + changed + ", unresolved=" + unresolved.size());
        check(unresolved.isEmpty(), "all installed recipes referencing these materials are covered; inspect report");
    }
}
