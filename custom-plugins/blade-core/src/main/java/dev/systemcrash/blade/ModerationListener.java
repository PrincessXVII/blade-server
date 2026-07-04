package dev.systemcrash.blade;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

public final class ModerationListener implements Listener {
    private final BladeCorePlugin plugin;

    public ModerationListener(BladeCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        PunishmentRecord ban = plugin.getPunishmentManager().getActiveBan(
                event.getPlayer().getUniqueId(),
                event.getAddress() == null ? "" : event.getAddress().getHostAddress()
        );
        if (ban == null) {
            return;
        }
        event.disallow(
                PlayerLoginEvent.Result.KICK_BANNED,
                "§cYou are banned from Blade.\n§7Reason: §f" + ban.getReason()
                        + "\n§7By: §f" + ban.getStaffName()
                        + "\n§7Expires: §f" + formatExpiry(ban)
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getRankManager().applyOperatorState(event.getPlayer(), getPrimaryGroup(event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        PunishmentRecord mute = plugin.getPunishmentManager().getActiveMute(event.getPlayer().getUniqueId());
        if (mute == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cYou are muted.\n§7Reason: §f" + mute.getReason()
                + "\n§7Expires: §f" + formatExpiry(mute));
    }

    private String formatExpiry(PunishmentRecord record) {
        if (record.isPermanent()) {
            return "never";
        }
        long remaining = Math.max(0L, record.getExpiresAt() - System.currentTimeMillis()) / 1000L;
        return DurationParser.formatSeconds(remaining);
    }

    private String getPrimaryGroup(java.util.UUID uuid) {
        try {
            return net.luckperms.api.LuckPermsProvider.get()
                    .getUserManager()
                    .getUser(uuid)
                    .getPrimaryGroup();
        } catch (Exception ignored) {
            return "default";
        }
    }
}
