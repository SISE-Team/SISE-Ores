package local.sise.ores.probe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.FluidStack;

/** Registry evidence from a disposable client; translation files alone contain stale entries. */
final class FluidInventory {
    static void export(MinecraftServer server,Path directory) {
        var fluids=new TreeMap<String,Object>();
        for(var fluid:BuiltInRegistries.FLUID) {
            var type=fluid.getFluidType();
            var info=new TreeMap<String,Object>();
            info.put("class",fluid.getClass().getName());
            info.put("type",net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES.getKey(type).toString());
            info.put("description_id",type.getDescriptionId());info.put("name_zh",I18n.get(type.getDescriptionId()));
            info.put("bucket",BuiltInRegistries.ITEM.getKey(fluid.getBucket()).toString());
            info.put("density",type.getDensity());info.put("viscosity",type.getViscosity());info.put("temperature",type.getTemperature());
            info.put("source",BuiltInRegistries.FLUID.getKey(fluid instanceof FlowingFluid flow?flow.getSource():fluid).toString());
            info.put("flowing",BuiltInRegistries.FLUID.getKey(fluid instanceof FlowingFluid flow?flow.getFlowing():fluid).toString());
            fluids.put(BuiltInRegistries.FLUID.getKey(fluid).toString(),info);
        }
        var tags=new TreeMap<String,List<String>>();
        BuiltInRegistries.FLUID.getTags().forEach(tag->tags.put(tag.key().location().toString(),tag.stream().map(h->BuiltInRegistries.FLUID.getKey(h.value()).toString()).sorted().toList()));
        var chemicals=new TreeMap<String,Object>();
        for(var chemical:mekanism.api.MekanismAPI.CHEMICAL_REGISTRY) {
            var info=new TreeMap<String,Object>();
            info.put("description_id",chemical.getTranslationKey());info.put("name_zh",I18n.get(chemical.getTranslationKey()));
            info.put("tint",chemical.getTint());info.put("class",chemical.getClass().getName());
            chemicals.put(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical).toString(),info);
        }
        var items=new TreeMap<String,Object>();
        for(var item:BuiltInRegistries.ITEM) {
            var id=BuiltInRegistries.ITEM.getKey(item).toString();
            if(id.contains("uu_matter") || id.contains("uumatter"))items.put(id,Map.of("name",item.getDefaultInstance().getHoverName().getString(),"class",item.getClass().getName()));
        }
        try {Files.writeString(directory.resolve("fluid-inventory.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("fluids",fluids,"fluid_tags",tags,"chemicals",chemicals,"uu_items",items)));}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
