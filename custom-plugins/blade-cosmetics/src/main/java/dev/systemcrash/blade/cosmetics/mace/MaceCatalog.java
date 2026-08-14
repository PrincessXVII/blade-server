package dev.systemcrash.blade.cosmetics.mace;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MaceCatalog {
    private final Map<String, MaceDefinition> byId;
    private final List<MaceDefinition> ordered;

    private MaceCatalog(Map<String, MaceDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static MaceCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("maces.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<MaceDefinition>>() {}.getType();
            List<MaceDefinition> list = new Gson().fromJson(reader, type);
            Map<String, MaceDefinition> map = new LinkedHashMap<>();
            for (MaceDefinition mace : list) {
                map.put(mace.id(), mace);
            }
            return new MaceCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load maces.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<MaceDefinition> all() {
        return ordered;
    }

    public Optional<MaceDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<MaceDefinition> page(int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            return List.of();
        }
        int from = page * pageSize;
        if (from >= ordered.size()) {
            return List.of();
        }
        int to = Math.min(ordered.size(), from + pageSize);
        return ordered.subList(from, to);
    }

    public int pageCount(int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        return (ordered.size() + pageSize - 1) / pageSize;
    }
}
