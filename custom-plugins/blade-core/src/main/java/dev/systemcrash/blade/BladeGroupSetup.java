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

    public static void ensureGroups(JavaPlugin plugin, LuckPerms luckPerms) {
        if (plugin.getConfig().getBoolean("groups-initialized", false)) {
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
                group.data().clear(node -> node instanceof PrefixNode || node instanceof PermissionNode || node instanceof WeightNode);
                if (!definition.prefix().isEmpty()) {
                    group.data().add(PrefixNode.builder(definition.prefix(), 100).build());
                }
                if (groupWeight > 0) {
                    group.data().add(WeightNode.builder(groupWeight).build());
                }
                for (String permission : definition.permissions()) {
                    group.data().add(PermissionNode.builder(permission).value(true).build());
                }
            });
        }

        plugin.getConfig().set("groups-initialized", true);
        plugin.saveConfig();
        plugin.getLogger().info("Blade LuckPerms groups initialized.");
    }

    private static Map<String, GroupDefinition> buildGroups() {
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        groups.put("default", new GroupDefinition("", List.of()));
        groups.put("trial", rank("\uE000"));
        groups.put("booster", rank("\uE001"));
        groups.put("chamber", rank("\uE002"));
        groups.put("razor", rank("\uE003", "blade.mute", "blade.mute.max.3600"));
        groups.put("winner", rank("\uE004", "blade.mute", "blade.mute.max.10800"));
        groups.put("sponsor", rank("\uE005", "blade.mute", "blade.mute.max.21600"));
        groups.put("stazher", rank("\uE006",
                "blade.mute", "blade.mute.max.604800",
                "blade.ban", "blade.ban.max.604800"));
        groups.put("helper", rank("\uE007",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.1209600",
                "blade.ban.ip", "blade.unmute", "blade.unban"));
        groups.put("moder", rank("\uE008",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.2592000",
                "blade.ban.ip", "blade.unmute", "blade.unban"));
        groups.put("stmoder", rank("\uE009",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.15552000",
                "blade.ban.ip", "blade.unmute", "blade.unban"));
        groups.put("glmoder", rank("\uE00A",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.permanent",
                "blade.ban.ip", "blade.unmute", "blade.unban"));
        groups.put("dizainer", rank("\uE00B"));
        groups.put("tehadmin", rank("\uE00C"));
        groups.put("kurator", rank("\uE00D",
                "blade.rank.grant",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.1209600",
                "blade.ban.ip", "blade.unmute", "blade.unban"));
        groups.put("zamestitel", rank("\uE00E", "blade.op.grant"));
        groups.put("owner", rank("\uE00F", "blade.admin"));
        return groups;
    }

    private static GroupDefinition rank(String icon, String... permissions) {
        return new GroupDefinition("§f" + icon + " ", List.of(permissions));
    }

    private record GroupDefinition(String prefix, List<String> permissions) {
    }
}
