package dev.systemcrash.blade.cosmetics.sword;

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

public final class SwordCatalog {
    private final Map<String, SwordDefinition> byId;
    private final List<SwordDefinition> ordered;

    private SwordCatalog(Map<String, SwordDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static SwordCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("swords.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<SwordDefinition>>() {}.getType();
            List<SwordDefinition> list = new Gson().fromJson(reader, type);
            Map<String, SwordDefinition> map = new LinkedHashMap<>();
            for (SwordDefinition sword : list) {
                map.put(sword.id(), sword);
            }
            return new SwordCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load swords.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<SwordDefinition> all() {
        return ordered;
    }

    public Optional<SwordDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<SwordDefinition> page(int page, int pageSize) {
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
