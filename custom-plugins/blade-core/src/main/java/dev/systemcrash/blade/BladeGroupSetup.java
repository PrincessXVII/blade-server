package dev.systemcrash.blade;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BladeGroupSetup {
    private BladeGroupSetup() {
    }

    private static final int RANK_CHAR_VERSION = 3;
    /** Bump when voicechat permission grants need to re-apply to all groups. */
    private static final int VOICECHAT_PERMS_VERSION = 2;

    private static final List<String> VOICECHAT_PERMISSIONS = List.of(
            "voicechat.listen",
            "voicechat.speak",
            "voicechat.groups"
    );

    public static void ensureGroups(JavaPlugin plugin, LuckPerms luckPerms) {
        boolean firstInit = !plugin.getConfig().getBoolean("groups-initialized", false);
        boolean prefixRefresh = plugin.getConfig().getInt("rank-char-version", 1) < RANK_CHAR_VERSION;
        boolean voicechatPerms = plugin.getConfig().getInt("voicechat-perms-version", 0) < VOICECHAT_PERMS_VERSION;
        Map<String, GroupDefinition> groups = buildGroups();
        boolean missingGroup = groups.keySet().stream()
                .anyMatch(name -> luckPerms.getGroupManager().getGroup(name) == null);

        if (!firstInit && !prefixRefresh && !voicechatPerms && !missingGroup) {
            return;
        }

        for (Map.Entry<String, GroupDefinition> entry : groups.entrySet()) {
            String name = entry.getKey();
            GroupDefinition definition = entry.getValue();
            boolean created = false;
            if (luckPerms.getGroupManager().getGroup(name) == null) {
                luckPerms.getGroupManager().createAndLoadGroup(name).join();
                created = true;
            }

            boolean applyFull = firstInit || created;
            luckPerms.getGroupManager().modifyGroup(name, group -> {
                if (applyFull) {
                    group.data().clear(node -> node instanceof PrefixNode || node instanceof PermissionNode || node instanceof WeightNode);
                    if (definition.weight() > 0) {
                        group.data().add(WeightNode.builder(definition.weight()).build());
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
                if (voicechatPerms || applyFull) {
                    grantVoicechat(group);
                }
            });
        }

        if (voicechatPerms) {
            Set<String> ensured = new LinkedHashSet<>(groups.keySet());
            for (Group existing : luckPerms.getGroupManager().getLoadedGroups()) {
                if (ensured.contains(existing.getName())) {
                    continue;
                }
                luckPerms.getGroupManager().modifyGroup(existing.getName(), BladeGroupSetup::grantVoicechat);
            }
        }

        plugin.getConfig().set("groups-initialized", true);
        plugin.getConfig().set("rank-char-version", RANK_CHAR_VERSION);
        plugin.getConfig().set("voicechat-perms-version", VOICECHAT_PERMS_VERSION);
        plugin.saveConfig();
        if (firstInit) {
            plugin.getLogger().info("Blade LuckPerms groups initialized.");
        } else if (prefixRefresh || missingGroup) {
            plugin.getLogger().info("Blade ranks refreshed (icons/groups).");
        }
        if (voicechatPerms) {
            plugin.getLogger().info("Granted voicechat.listen/speak/groups to all LuckPerms groups.");
        }
    }

    private static void grantVoicechat(Group group) {
        for (String permission : VOICECHAT_PERMISSIONS) {
            group.data().add(PermissionNode.builder(permission).value(true).build());
        }
    }

    private static Map<String, GroupDefinition> buildGroups() {
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        groups.put("default", new GroupDefinition("", 0, VOICECHAT_PERMISSIONS));
        groups.put("trial", rank(10, "\uE100"));
        groups.put("booster", rank(20, "\uE101"));
        groups.put("chamber", rank(30, "\uE102"));
        groups.put("razor", rank(40, "\uE103", "blade.mute", "blade.mute.max.3600"));
        groups.put("winner", rank(50, "\uE104", "blade.mute", "blade.mute.max.10800"));
        groups.put("sponsor", rank(60, "\uE105", "blade.mute", "blade.mute.max.21600"));
        // Media glyph is packed after titles → U+E114 (stable existing rank codepoints).
        groups.put("media", rank(65, "\uE114"));
        groups.put("stazher", rank(70, "\uE106",
                "blade.mute", "blade.mute.max.604800",
                "blade.ban", "blade.ban.max.604800",
                "blade.kick", "blade.warn", "blade.history"));
        groups.put("helper", rank(80, "\uE107",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.1209600",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.kick", "blade.warn", "blade.history"));
        groups.put("moder", rank(90, "\uE108",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.2592000",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise", "blade.kick", "blade.warn", "blade.history", "blade.pcban"));
        groups.put("stmoder", rank(100, "\uE109",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.15552000",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise", "blade.kick", "blade.warn", "blade.history", "blade.pcban"));
        groups.put("glmoder", rank(110, "\uE10A",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.permanent",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise", "blade.kick", "blade.warn", "blade.history", "blade.pcban"));
        groups.put("dizainer", rank(120, "\uE10B"));
        groups.put("tehadmin", rank(130, "\uE10C"));
        groups.put("kurator", rank(140, "\uE10D",
                "blade.rank.grant",
                "blade.mute", "blade.mute.unlimited",
                "blade.ban", "blade.ban.max.1209600",
                "blade.ban.ip", "blade.unmute", "blade.unban",
                "blade.revise", "blade.kick", "blade.warn", "blade.history", "blade.pcban"));
        groups.put("zamestitel", rank(150, "\uE10E", "blade.op.grant", "blade.revise",
                "blade.kick", "blade.warn", "blade.history", "blade.pcban"));
        groups.put("owner", rank(160, "\uE10F", "blade.admin", "blade.revise", "blade.spawn.bypass"));
        return groups;
    }

    private static GroupDefinition rank(int weight, String icon, String... permissions) {
        return new GroupDefinition("§f" + icon + " ", weight, List.of(permissions));
    }

    private record GroupDefinition(String prefix, int weight, List<String> permissions) {
    }
}
