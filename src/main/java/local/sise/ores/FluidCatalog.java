package local.sise.ores;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import mekanism.api.chemical.Chemical;

/** Reviewed fluid identities. Registry lookups are lazy so bootstrap never loads game registries. */
public final class FluidCatalog {
    public record Entry(String name,String preferred,List<String> members,List<String> aliases,List<String> buckets,String tag) {
        public String target(){return "sise_ores:"+name;}
    }
    public record ChemicalEntry(String name,String preferred,List<String> members) {
        public String target(){return "sise_ores:"+name;}
    }
    public static final List<Entry> FLUIDS;
    public static final List<ChemicalEntry> CHEMICALS;
    public static final Map<String,Entry> BY_SOURCE;
    public static final Map<String,ChemicalEntry> CHEMICAL_BY_SOURCE;
    static {
        try(var in=Objects.requireNonNull(FluidCatalog.class.getResourceAsStream("/sise_ores/fluids.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();var gson=new Gson();
            var entries=new ArrayList<Entry>();var sources=new HashMap<String,Entry>();
            for(var value:root.getAsJsonArray("fluids")) {
                var entry=gson.fromJson(value,Entry.class);entries.add(entry);
                for(var source:entry.aliases())if(sources.put(source,entry)!=null)throw new IllegalStateException("Ambiguous fluid "+source);
                sources.put("sise_ores:flowing_"+entry.name(),entry);
            }
            FLUIDS=List.copyOf(entries);BY_SOURCE=Map.copyOf(sources);
            var chemicals=new ArrayList<ChemicalEntry>();var chemicalSources=new HashMap<String,ChemicalEntry>();
            for(var value:root.getAsJsonArray("chemicals")) {
                var entry=gson.fromJson(value,ChemicalEntry.class);chemicals.add(entry);
                for(var source:entry.members())chemicalSources.put(source,entry);
            }
            CHEMICALS=List.copyOf(chemicals);CHEMICAL_BY_SOURCE=Map.copyOf(chemicalSources);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static Holder<Fluid> canonical(Holder<Fluid> source) {
        if(source==null)return null;
        var key=source.unwrapKey().orElse(null);
        if(key==null)return source;
        var entry=BY_SOURCE.get(key.identifier().toString());
        if(entry==null)return source;
        return BuiltInRegistries.FLUID.get(Identifier.parse(entry.target())).<Holder<Fluid>>map(h->h).orElse(source);
    }
    public static Fluid canonical(Fluid source) {
        if(source==null)return null;
        return canonical(BuiltInRegistries.FLUID.wrapAsHolder(source)).value();
    }
    public static Holder<Chemical> canonicalChemical(Holder<Chemical> source) {
        if(source==null)return null;
        var key=source.unwrapKey().orElse(null);if(key==null)return source;
        var entry=CHEMICAL_BY_SOURCE.get(key.identifier().toString());if(entry==null)return source;
        return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(Identifier.parse(entry.target())).<Holder<Chemical>>map(h->h).orElse(source);
    }
    public static Chemical canonicalChemical(Chemical source) {
        return canonicalChemical(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.wrapAsHolder(source)).value();
    }
    public static HolderSet<Fluid> canonicalSet(HolderSet<Fluid> set) {
        if(set.unwrapKey().isPresent())return set;
        return HolderSet.direct(set.stream().map(FluidCatalog::canonical).distinct().toList());
    }
    public static HolderSet<Chemical> canonicalChemicalSet(HolderSet<Chemical> set) {
        if(set.unwrapKey().isPresent())return set;
        return HolderSet.direct(set.stream().map(FluidCatalog::canonicalChemical).distinct().toList());
    }
    private FluidCatalog(){}
}
