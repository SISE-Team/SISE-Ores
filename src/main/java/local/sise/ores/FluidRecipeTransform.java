package local.sise.ores;

import com.google.gson.*;
import java.util.*;

/** Registry-specific fluid/chemical fields; never rewrite component payloads or conditions. */
public final class FluidRecipeTransform {
    public static int apply(JsonObject recipe){return walk(recipe,0);}
    private static int walk(JsonElement value,int domain){
        int count=0;
        if(value.isJsonArray()) {
            var array=value.getAsJsonArray();
            for(int i=0;i<array.size();i++){
                var source=array.get(i);
                var target=identity(source,domain);
                if(target!=null){array.set(i,new JsonPrimitive(target));count++;}
                else count+=walk(source,domain);
            }
        }else if(value.isJsonObject()) {
            var object=value.getAsJsonObject();
            for(var entry:object.entrySet()){
                var key=entry.getKey();
                if(Set.of("components","nbt","data","neoforge:conditions").contains(key))continue;
                int child=key.toLowerCase(Locale.ROOT).contains("chemical")?2:key.toLowerCase(Locale.ROOT).contains("fluid")?1:domain;
                if(object.has("type") && key.equals("output") && Set.of("mekanism:chemical_conversion","mekanism:oxidizing","mekanism:chemical_infusing","mekanism:centrifuging","mekanism:activating","mekanism:dissolution").contains(object.get("type").getAsString()))child=2;
                var target=identity(entry.getValue(),child);
                if(target!=null){entry.setValue(new JsonPrimitive(target));count++;}
                else count+=walk(entry.getValue(),child);
            }
        }
        return count;
    }
    private static String identity(JsonElement value,int domain){
        if(domain==0 || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())return null;
        String id=value.getAsString();
        if(domain==1){var entry=FluidCatalog.BY_SOURCE.get(id);return entry==null?null:entry.target();}
        var entry=FluidCatalog.CHEMICAL_BY_SOURCE.get(id);return entry==null?null:entry.target();
    }
    private FluidRecipeTransform(){}
}
