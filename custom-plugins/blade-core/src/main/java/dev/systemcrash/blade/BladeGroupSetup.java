package dev.systemcrash.blade;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BladeGroupSetup {
    private BladeGroupSetup() {
    }

    private static final int RANK_CHAR_VERSION = 2;

    public static void ensureGroups(JavaPlugin plugin, LuckPerms luckPerms) {
        boolean firstInit = !plugin.getConfig().getBoolean("groups-initialized", false);
        boolean prefixRefresh = plugin.getConfig().getInt("rank-char-version", 1) < RANK_CHAR_VERSION;

        if (!firstInit && !prefixRefresh) {
            return;
        }

        Map<String, GroupDefinition> groups = buildGroups();
        int weight = 0;
        for (Map.Entry<String, GroupDefinition> entry : groups.entrySet()) {
            String name = entry.getKey();
            GroupDefinition definition = entry.getValue();
            weight += name.equals("default") ? 0 : 10;

            if (luckPerms.getGroupManager().getGroup(name) == null) {
                luckPerms.getGroupManager().createAndLoadGroup(name).join();
            }

            int groupWeight = weight;
            luckPerms.getGroupManager().modifyGroup(name, group -> {
                if (firstInit) {
                    group.data().clear(node -> node instanceof PrefixNode || node instanceof PermissionNode || node instanceof WeightNode);
                    if (groupWeight > 0) {
                        group.data().add(WeightNode.builder(groupWeight).build());
                    }
                    for (String permission : definition.permissions()) {
                        group.data().add(PermissionNode.builder(permission).value(true).build());
                    }
                } else {
                    group.data().clear(PrefixNode.class::isInstance);
                }
                if (!definition.prefix().isEmpty()) {
                    group.data().add(PrefixNode.builder(definition.prefix(), 100).build());
                }
            });
        }

        plugin.getConfig().set("groups-initialized", true);
        plugin.getConfig().set("rank-char-version", RANK_CHAR_VERSION);
        plugin.saveConfig();
        plugin.getLogger().info(firstInit
                ? "Blade LuckPerms groups initialized."
                : "Blade rank prefix icons refreshed for merged resource pack.");
    }

    private static Map<String, GroupDefinition> buildGroups() {
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        groups.put("default", new GroupDefinition("", List.of()));
        groups.put("trial", rank("\uE100"));
        groups.put("booster", rank("\uE101"));
        groups.put("chamber", rank("\uE102"));
        groups.put("razor", rank("\uE103", "blade.mute", "blade.mute.max.3600"));
        groups.put("winner", rank("\uE104", "blade.mute", "blade.mute.max.10800"));
        groups.put("sponsor", rank("\uE105", "blade.mute", "blade.mute.max.21600"));
        groups.put("stazher", rank("\uE106",
                "blade.mute", "blade.mute.max.604800",
                "blade.ban", "blade.ban.max.604800"));
        groups.put("helper", rank("\uE107",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.1209600",
                "blade.ban.ip", "blade.unmute", "blade.unban"));
        groups.put("moder", rank("\uE108",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.2592000",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise"));
        groups.put("stmoder", rank("\uE109",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.15552000",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise"));
        groups.put("glmoder", rank("\uE10A",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.permanent",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise"));
        groups.put("dizainer", rank("\uE10B"));
        groups.put("tehadmin", rank("\uE10C"));
        groups.put("kurator", rank("\uE10D",
                "blade.rank.grant",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.1209600",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise"));
        groups.put("zamestitel", rank("\uE10E", "blade.op.grant", "blade.revise"));
        groups.put("owner", rank("\uE10F", "blade.admin", "blade.revise"));
        return groups;
    }

    private static GroupDefinition rank(String icon, String... permissions) {
        return new GroupDefinition("§f" + icon + " ", List.of(permissions));
    }

    private record GroupDefinition(String prefix, List<String> permissions) {
    }
}
