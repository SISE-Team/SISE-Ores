package local.sise.ores;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit, reviewed identities. Tag membership is never used to discover equivalence. */
public final class MaterialCatalog {
    public record Material(String name, String material, String form, String preferred, List<String> members, String tag, String canonical, String group) {
        public String target() { return canonical == null ? "sise_ores:" + name : canonical; }
        public boolean owned() { return canonical == null; }
        public String inputTag() { return "#sise_ores:equivalents/" + (group == null ? tag.substring(2) : group); }
    }
    public static final List<Material> MATERIALS;
    public static final Map<String, Material> BY_SOURCE;
    public static final Map<String, Material> BY_TARGET;
    public static final Map<String, Material> BY_OUTPUT_TAG;
    static {
        try (var stream = Objects.requireNonNull(MaterialCatalog.class.getResourceAsStream("/sise_ores/materials.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var array = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("materials");
            var materials = new ArrayList<Material>();
            var sources = new LinkedHashMap<String, Material>();
            var names = new HashSet<String>();
            for (var entry : array) {
                var m = new Gson().fromJson(entry, Material.class);
                if (!names.add(m.name())) throw new IllegalStateException("Duplicate material " + m.name());
                materials.add(m);
                for (var source : m.members()) {
                    if (sources.putIfAbsent(source, m) != null) throw new IllegalStateException("Ambiguous material " + source);
                }
            }
            MATERIALS = List.copyOf(materials);
            BY_SOURCE = Collections.unmodifiableMap(sources);
            var targets = new HashMap<String, Material>();
            for (var material : materials) targets.put(material.target(), material);
            BY_TARGET = Map.copyOf(targets);
            var tags = new HashMap<String, Material>();
            var ambiguous = new HashSet<String>();
            for (var m : materials) if (tags.putIfAbsent(m.tag(), m) != null) ambiguous.add(m.tag());
            ambiguous.forEach(tags::remove);
            BY_OUTPUT_TAG = Map.copyOf(tags);
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private MaterialCatalog() {}
}
