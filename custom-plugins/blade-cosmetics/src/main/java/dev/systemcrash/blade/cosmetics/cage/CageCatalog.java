package dev.systemcrash.blade.cosmetics.cage;

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

public final class CageCatalog {
    private final Map<String, CageDefinition> byId;
    private final List<CageDefinition> ordered;

    private CageCatalog(Map<String, CageDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static CageCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("cages.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<CageDefinition>>() {}.getType();
            List<CageDefinition> list = new Gson().fromJson(reader, type);
            Map<String, CageDefinition> map = new LinkedHashMap<>();
            for (CageDefinition cage : list) {
                map.put(cage.id(), cage);
            }
            return new CageCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load cages.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<CageDefinition> all() {
        return ordered;
    }

    public Optional<CageDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<CageDefinition> page(int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            return List.of();
        }
        int from = page * pageSize;
        if (from >= ordered.size()) {
            return List.of();
        }
        return ordered.subList(from, Math.min(ordered.size(), from + pageSize));
    }

    public int pageCount(int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        return (ordered.size() + pageSize - 1) / pageSize;
    }
}
