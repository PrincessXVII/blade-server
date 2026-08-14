package dev.systemcrash.blade.cosmetics.titlecolor;

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

public final class TitleColorCatalog {
    private final Map<String, TitleColorDefinition> byId;
    private final List<TitleColorDefinition> ordered;

    private TitleColorCatalog(Map<String, TitleColorDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static TitleColorCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("title_colors.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<TitleColorDefinition>>() {}.getType();
            List<TitleColorDefinition> list = new Gson().fromJson(reader, type);
            Map<String, TitleColorDefinition> map = new LinkedHashMap<>();
            for (TitleColorDefinition color : list) {
                map.put(color.id(), color);
            }
            return new TitleColorCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load title_colors.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<TitleColorDefinition> all() {
        return ordered;
    }

    public Optional<TitleColorDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<TitleColorDefinition> page(int page, int pageSize) {
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
