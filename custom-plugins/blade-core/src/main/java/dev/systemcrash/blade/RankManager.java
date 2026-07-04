package dev.systemcrash.blade;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RankManager {
    private final BladeCorePlugin plugin;

    public RankManager(BladeCorePlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> setRank(CommandSender actor, String targetName, String groupName) {
        String normalizedGroup = groupName.toLowerCase(Locale.ROOT);
        if (!canGrantGroup(actor, normalizedGroup)) {
            actor.sendMessage("§cYou cannot grant the rank §f" + normalizedGroup + "§c.");
            return CompletableFuture.completedFuture(false);
        }

        LuckPerms luckPerms = LuckPermsProvider.get();
        Group group = luckPerms.getGroupManager().getGroup(normalizedGroup);
        if (group == null) {
            actor.sendMessage("§cUnknown rank: §f" + normalizedGroup);
            return CompletableFuture.completedFuture(false);
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = offlinePlayer.getUniqueId();
        return luckPerms.getUserManager().modifyUser(uuid, user -> {
            user.data().clear(node -> node instanceof InheritanceNode);
            user.data().add(InheritanceNode.builder(normalizedGroup).build());
        }).thenApply(v -> {
            applyOperatorState(offlinePlayer, normalizedGroup);
            actor.sendMessage("§aSet rank §f" + normalizedGroup + " §afor §f" + targetName + "§a.");
            Player online = offlinePlayer.getPlayer();
            if (online != null && online.isOnline()) {
                online.sendMessage("§aYour rank is now §f" + normalizedGroup + "§a.");
            }
            return true;
        });
    }

    public CompletableFuture<Boolean> grantOperator(CommandSender actor, String targetName) {
        if (!actor.hasPermission("blade.op.grant") && !actor.hasPermission("blade.admin")) {
            actor.sendMessage("§cYou cannot grant operator.");
            return CompletableFuture.completedFuture(false);
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        offlinePlayer.setOp(true);
        actor.sendMessage("§aGranted operator to §f" + targetName + "§a.");
        Player online = offlinePlayer.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage("§aYou have been granted operator.");
        }
        return CompletableFuture.completedFuture(true);
    }

    public void applyOperatorState(OfflinePlayer player, String groupName) {
        List<String> opGroups = plugin.getConfig().getStringList("op-groups");
        if (opGroups.contains(groupName)) {
            player.setOp(true);
        }
    }

    public void syncOnlineOperators() {
        List<String> opGroups = plugin.getConfig().getStringList("op-groups");
        LuckPerms luckPerms = LuckPermsProvider.get();
        for (Player player : Bukkit.getOnlinePlayers()) {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                continue;
            }
            boolean shouldBeOp = user.getNodes().stream()
                    .filter(node -> node.getKey().startsWith("group."))
                    .anyMatch(node -> opGroups.contains(node.getKey().substring("group.".length())));
            player.setOp(shouldBeOp || player.isOp() && player.hasPermission("blade.admin"));
        }
    }

    private boolean canGrantGroup(CommandSender actor, String groupName) {
        if ("owner".equals(groupName) && !actor.hasPermission("blade.admin")) {
            return false;
        }
        if (actor.hasPermission("blade.admin")) {
            return true;
        }
        if (!actor.hasPermission("blade.rank.grant")) {
            return false;
        }
        List<String> allowed = plugin.getConfig().getStringList("curator-groups");
        return allowed.contains(groupName);
    }
}
