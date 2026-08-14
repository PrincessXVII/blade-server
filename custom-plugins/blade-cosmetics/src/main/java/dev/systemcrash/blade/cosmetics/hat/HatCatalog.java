package dev.systemcrash.blade.cosmetics.hat;

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

public final class HatCatalog {
    private final Map<String, HatDefinition> byId;
    private final List<HatDefinition> ordered;

    private HatCatalog(Map<String, HatDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static HatCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("hats.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<HatDefinition>>() {}.getType();
            List<HatDefinition> list = new Gson().fromJson(reader, type);
            Map<String, HatDefinition> map = new LinkedHashMap<>();
            for (HatDefinition hat : list) {
                map.put(hat.id(), hat);
            }
            return new HatCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load hats.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<HatDefinition> all() {
        return ordered;
    }

    public Optional<HatDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<HatDefinition> page(int page, int pageSize) {
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
