package dev.systemcrash.blade.cosmetics.title;

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

public final class TitleCatalog {
    private final Map<String, TitleDefinition> byId;
    private final List<TitleDefinition> ordered;

    private TitleCatalog(Map<String, TitleDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static TitleCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("titles.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<TitleDefinition>>() {}.getType();
            List<TitleDefinition> list = new Gson().fromJson(reader, type);
            Map<String, TitleDefinition> map = new LinkedHashMap<>();
            for (TitleDefinition title : list) {
                map.put(title.id(), title);
            }
            return new TitleCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load titles.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<TitleDefinition> all() {
        return ordered;
    }

    public Optional<TitleDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<TitleDefinition> page(int page, int pageSize) {
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
