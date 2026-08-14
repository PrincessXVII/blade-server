package dev.systemcrash.blade.cosmetics.killeffect;

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

public final class KillEffectCatalog {
    private final Map<String, KillEffectDefinition> byId;
    private final List<KillEffectDefinition> ordered;

    private KillEffectCatalog(Map<String, KillEffectDefinition> byId) {
        this.byId = byId;
        this.ordered = List.copyOf(byId.values());
    }

    public static KillEffectCatalog load(JavaPlugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("kill_effects.json"), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<KillEffectDefinition>>() {}.getType();
            List<KillEffectDefinition> list = new Gson().fromJson(reader, type);
            Map<String, KillEffectDefinition> map = new LinkedHashMap<>();
            for (KillEffectDefinition effect : list) {
                map.put(effect.id(), effect);
            }
            return new KillEffectCatalog(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load kill_effects.json", ex);
        }
    }

    public int size() {
        return ordered.size();
    }

    public List<KillEffectDefinition> all() {
        return ordered;
    }

    public Optional<KillEffectDefinition> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<KillEffectDefinition> page(int page, int pageSize) {
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
