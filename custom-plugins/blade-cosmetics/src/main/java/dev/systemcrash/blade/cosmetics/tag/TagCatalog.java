package dev.systemcrash.blade.cosmetics.tag;

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

public final class TagCatalog {
    private final Map<String, TagDefinition> byId;
    private final List<TagDefinition> ordered;

    private TagCatalog(Map<String, TagDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static TagCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("tags.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<TagDefinition>>() {}.getType();
            List<TagDefinition> list = new Gson().fromJson(reader, type);
            Map<String, TagDefinition> map = new LinkedHashMap<>();
            for (TagDefinition tag : list) {
                map.put(tag.id(), tag);
            }
            return new TagCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load tags.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<TagDefinition> all() {
        return ordered;
    }

    public Optional<TagDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<TagDefinition> page(int page, int pageSize) {
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
