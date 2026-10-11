package local.sise.ores.probe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import local.sise.ores.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;

/** Disposable tests of real generation, real loot tables, mining tiers and recipe integration. */
final class WorldgenChecks {
    private static final List<String> checks = new ArrayList<>();
    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); checks.add(message); }
    private static Block block(String id) { return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)); }
    private static String id(BlockState s) { return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(); }
    private static boolean containsLegacy(JsonElement j) {
        if (j.isJsonPrimitive()) return j.getAsJsonPrimitive().isString() && OreWorldgen.SOURCES.containsKey(j.getAsString());
        if (j.isJsonArray()) { for (var e : j.getAsJsonArray()) if (containsLegacy(e)) return true; }
        if (j.isJsonObject()) { for (var e : j.getAsJsonObject().entrySet()) if (containsLegacy(e.getValue())) return true; }
        return false;
    }
    private static void normalizeJson(JsonElement j) {
        if (j.isJsonArray()) j.getAsJsonArray().forEach(WorldgenChecks::normalizeJson);
        if (j.isJsonObject()) {
            var o = j.getAsJsonObject();
            if (o.has("Name") && OreWorldgen.SOURCES.containsKey(o.get("Name").getAsString()))
                o.addProperty("Name", OreWorldgen.SOURCES.get(o.get("Name").getAsString()).target());
            if (o.has("blockId") && OreWorldgen.SOURCES.containsKey(o.get("blockId").getAsString()))
                o.addProperty("blockId", OreWorldgen.SOURCES.get(o.get("blockId").getAsString()).target());
            if (o.has("boulderOres")) {
                var ores=o.getAsJsonArray("boulderOres");
                for(int i=0;i<ores.size();i++) if(OreWorldgen.SOURCES.containsKey(ores.get(i).getAsString()))
                    ores.set(i,new JsonPrimitive(OreWorldgen.SOURCES.get(ores.get(i).getAsString()).target()));
            }
            o.entrySet().forEach(e -> normalizeJson(e.getValue()));
        }
    }
    private static Map<String, Integer> scan(ServerLevel level, int cx, int cz, int radius) {
        var counts = new TreeMap<String, Integer>();
        for (int x = cx-radius; x <= cx+radius; x++) for (int z = cz-radius; z <= cz+radius; z++) {
            var chunk = level.getChunk(x,z);
            for (var section : chunk.getSections()) {
                if (section.hasOnlyAir()) continue;
                for (int dx=0;dx<16;dx++) for (int dy=0;dy<16;dy++) for (int dz=0;dz<16;dz++) {
                    var state = section.getBlockState(dx,dy,dz);
                    var name = id(state);
                    if (OreWorldgen.SOURCES.containsKey(name)) throw new IllegalStateException("Legacy ore naturally generated: " + name + " " + level.dimension() + " " + x + "," + z);
                    if (name.startsWith("sise_ores:") && name.endsWith("_ore")) counts.merge(name,1,Integer::sum);
                }
            }
        }
        return counts;
    }
    // Reuse actual loaded chunks to exercise the transformed region write without mocking setBlock.
    private static final class FixtureRegion extends WorldGenRegion {
        final ServerLevel fixture;
        FixtureRegion(ServerLevel level, ChunkAccess center) {
            super(level, StaticCache2D.<GenerationChunkHolder>create(center.getPos().x(),center.getPos().z(),0,(x,z)->null),
                ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
            fixture=level;
        }
        // Request FULL explicitly: asking for an earlier status of a finished chunk returns a
        // read-only ImposterProtoChunk, which intentionally discards setBlockState writes.
        @Override public ChunkAccess getChunk(int x,int z,ChunkStatus status,boolean load) { return fixture.getChunk(x,z); }
    }
    static void verify(MinecraftServer server, Path directory) {
        try {
            var report = new TreeMap<String,Object>();
            var level = server.overworld();
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var ores = MaterialCatalog.MATERIALS.stream().filter(m -> m.form().equals("ores")).toList();
            check(ores.size()==20 && OreWorldgen.SOURCES.size()==35,"20 reviewed ore variants from 35 legacy blocks");
            var registry = server.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
            var features = new TreeMap<String,JsonElement>();
            for (var holder : registry.listElements().toList()) {
                var json = ConfiguredFeature.DIRECT_CODEC.encodeStart(ops,holder.value()).getOrThrow();
                check(!containsLegacy(json),"no legacy configured-feature targets " + holder.key().identifier());
                features.put(holder.key().identifier().toString(),json);
            }
            var inputs = JsonParser.parseString(Files.readString(directory.resolve("worldgen-inventory.json"))).getAsJsonObject().getAsJsonObject("features");
            int changed=0;
            for (var entry : inputs.entrySet()) if (containsLegacy(entry.getValue())) {
                var path=entry.getKey().split("/",5);
                var name=path[1]+":"+path[4].substring(0,path[4].length()-5);
                var expected=entry.getValue().deepCopy(); normalizeJson(expected);
                var roundtrip=ConfiguredFeature.DIRECT_CODEC.encodeStart(ops,ConfiguredFeature.DIRECT_CODEC.parse(ops,expected).getOrThrow()).getOrThrow();
                check(roundtrip.equals(features.get(name)),"feature targets only; generator parameters unchanged " + name);
                changed++;
            }
            check(changed>=30,"all installed data-driven metal-ore generators covered");
            report.put("configured_features_checked",features.size()); report.put("changed_configured_features",changed);
            // Scan normally generated terrain BEFORE any artificial ore placements.
            var natural=scan(level,0,0,2);
            check(natural.values().stream().mapToInt(i->i).sum()>100,"normal overworld generation contains canonical ores");
            check(natural.size()>=6,"multiple canonical ores found in naturally generated terrain");
            var end=Objects.requireNonNull(server.getLevel(Level.END));
            var naturalEnd=scan(end,80,80,1);
            report.put("overworld_chunks_scanned",25); report.put("end_chunks_scanned",9);
            report.put("natural_overworld_ores",natural); report.put("natural_end_ores",naturalEnd);
            report.put("legacy_ore_blocks_in_scanned_chunks",0);
            var pos=new BlockPos(8,200,8);
            var region=new FixtureRegion(level,level.getChunk(0,0));
            var diamond=new ItemStack(Items.DIAMOND_PICKAXE);
            var silk=diamond.copy(); silk.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
            var fortune=diamond.copy(); fortune.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE),3);
            var mining=new TreeMap<String,Object>();
            for (var m : ores) {
                var source=block(m.preferred()); var target=block(m.target());
                check(source.getStateDefinition().getPossibleStates().size()==1 && target.getStateDefinition().getPossibleStates().size()==1,"reviewed stateless ore " + m.name());
                check(target.defaultDestroyTime()==source.defaultDestroyTime(),"priority-source hardness " + m.name());
                check(target.defaultBlockState().getSoundType().equals(source.defaultBlockState().getSoundType()),"priority-source sound " + m.name());
                check(target.defaultBlockState().is(TagKey.create(Registries.BLOCK,Identifier.parse(m.tag()))),"ore block tag " + m.name());
                check(target.defaultBlockState().is(TagKey.create(Registries.BLOCK,Identifier.parse("c:ores_in_ground/"+m.group().split("/")[2]))),"host stone remains distinct " + m.name());
                for (var tool : List.of(Items.WOODEN_PICKAXE,Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.DIAMOND_PICKAXE))
                    check(new ItemStack(tool).isCorrectToolForDrops(source.defaultBlockState()) == new ItemStack(tool).isCorrectToolForDrops(target.defaultBlockState()),"priority-source mining tier " + m.name()+" "+tool);
                level.setBlock(pos,target.defaultBlockState(),3);
                var normal=Block.getDrops(target.defaultBlockState(),level,pos,null,null,diamond);
                check(normal.size()==1 && normal.getFirst().is(BuiltInRegistries.ITEM.getValue(SiseOres.id("raw_"+m.material()))) && normal.getFirst().getCount()==1,"ordinary mining gives canonical raw material " + m.name());
                var silkDrop=Block.getDrops(target.defaultBlockState(),level,pos,null,null,silk);
                check(silkDrop.size()==1 && silkDrop.getFirst().is(target.asItem()) && silkDrop.getFirst().getCount()==1,"silk touch gives canonical ore " + m.name());
                int total=0,max=0;
                for(int i=0;i<48;i++) {
                    var drop=Block.getDrops(target.defaultBlockState(),level,pos,null,null,fortune);
                    check(drop.size()==1 && drop.getFirst().is(normal.getFirst().getItem()) && drop.getFirst().getCount()>=1 && drop.getFirst().getCount()<=4,"fortune III preserves raw identity and yield bounds " + m.name()+" "+i);
                    total+=drop.getFirst().getCount(); max=Math.max(max,drop.getFirst().getCount());
                }
                check(total>48 && max>1,"fortune actually increases yield " + m.name());
                mining.put(m.name(),Map.of("preferred",m.preferred(),"normal_drop",normal.getFirst().toString(),"silk_drop",silkDrop.getFirst().toString(),"fortune_samples",48,"fortune_total",total));
            }
            var placements=new TreeMap<String,Integer>();
            for (var entry : OreWorldgen.SOURCES.entrySet()) {
                var source=block(entry.getKey()); var target=block(entry.getValue().target());
                var host=switch(entry.getValue().group().split("/")[2]) {case "deepslate"->Blocks.DEEPSLATE;case "end_stone"->Blocks.END_STONE;default->Blocks.STONE;};
                var predicate=new BlockMatchTest(host);
                var configured=OreConfiguration.target(predicate,source.defaultBlockState());
                check(configured.target==predicate && configured.state.is(target),"constructor canonicalizes target without changing host predicate " + entry.getKey());
                level.setBlock(pos,source.defaultBlockState(),3);
                check(level.getBlockState(pos).is(source),"ordinary existing/player block placement stays unchanged " + entry.getKey());
                region.setBlock(pos,source.defaultBlockState(),3,512);
                check(level.getBlockState(pos).is(target),"generation-region placement canonicalized " + entry.getKey()+" actual="+id(level.getBlockState(pos))+" chunk="+region.getChunk(0,0).getClass().getName());
                for(var p:BlockPos.betweenClosed(0,192,0,15,207,15)) level.setBlock(p,host.defaultBlockState(),2);
                var feature=new ConfiguredFeature<>(Feature.ORE,new OreConfiguration(List.of(configured),16,0));
                feature.place(region,level.getChunkSource().getGenerator(),RandomSource.create(602),pos);
                int placed=0,legacy=0;
                for(var p:BlockPos.betweenClosed(0,192,0,15,207,15)) {
                    var state=level.getBlockState(p);
                    if(state.is(source)) legacy++;
                    if(state.is(target)) placed++;
                }
                check(legacy==0,"actual vein generation excludes legacy ore " + entry.getKey());
                check(placed>0,"real OreFeature places canonical vein " + entry.getKey());
                placements.put(entry.getKey(),placed);
            }
            for(String protectedId:List.of("minecraft:iron_ore","minecraft:deepslate_gold_ore","modern_industrialization:bauxite_ore","powah:uraninite_ore","powah:uraninite_ore_dense","ae2cs:charged_certus_quartz_ore","ltxi:raw_titanium_cluster")) {
                var state=block(protectedId).defaultBlockState();
                check(OreWorldgen.normalize(state)==state,"special/vanilla block untouched " + protectedId);
                region.setBlock(pos,state,3,512);
                check(level.getBlockState(pos).equals(state),"actual generation-region special block untouched " + protectedId);
            }
            report.put("mining",mining); report.put("actual_vein_placements",placements);
            report.put("passed",true); report.put("assertions",checks.size());
            Files.writeString(directory.resolve("worldgen-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
