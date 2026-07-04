package dev.systemcrash.blade;

import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

public final class BladeCorePlugin extends JavaPlugin {
    private PunishmentManager punishmentManager;
    private RankManager rankManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File storageFile = new File(getDataFolder(), getConfig().getString("storage-file", "punishments.yml"));
        punishmentManager = new PunishmentManager(storageFile);
        rankManager = new RankManager(this);

        getServer().getPluginManager().registerEvents(new ModerationListener(this), this);
        getServer().getScheduler().runTaskLater(this, () -> {
            BladeGroupSetup.ensureGroups(this, LuckPermsProvider.get());
            rankManager.syncOnlineOperators();
        }, 40L);
        getLogger().info("BladeCore enabled.");
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "tempmute" -> handleTempMute(sender, args);
            case "tempban" -> handleTempBan(sender, args);
            case "mute" -> handlePermanentMute(sender, args);
            case "ban" -> handlePermanentBan(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "ipban" -> handleIpBan(sender, args);
            case "rank" -> handleRank(sender, args);
            case "bladeop" -> handleBladeOp(sender, args);
            default -> false;
        };
    }

    private boolean handleTempMute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /tempmute <player> <duration> [reason]");
            return true;
        }
        if (!PermissionLimits.canMute(sender)) {
            sender.sendMessage("§cYou cannot mute players.");
            return true;
        }
        try {
            long duration = DurationParser.parseSeconds(args[1]);
            PermissionLimits.validateMuteDuration(sender, duration);
            return applyMute(sender, args[0], duration, joinReason(args, 2));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("§c" + exception.getMessage());
            return true;
        }
    }

    private boolean handlePermanentMute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /mute <player> [reason]");
            return true;
        }
        if (!sender.hasPermission("blade.mute.unlimited") && !sender.hasPermission("blade.admin")) {
            sender.sendMessage("§cYou cannot permanently mute players.");
            return true;
        }
        return applyMute(sender, args[0], -1L, joinReason(args, 1));
    }

    private boolean applyMute(CommandSender sender, String targetName, long durationSeconds, String reason) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        punishmentManager.mute(
                target.getUniqueId(),
                target.getName() == null ? targetName : target.getName(),
                sender.getName(),
                reason,
                durationSeconds
        );
        sender.sendMessage("§aMuted §f" + targetName + " §afor §f" + DurationParser.formatSeconds(durationSeconds) + "§a.");
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage("§cYou have been muted by §f" + sender.getName() + "§c.");
        }
        return true;
    }

    private boolean handleTempBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /tempban <player> <duration> [reason]");
            return true;
        }
        if (!PermissionLimits.canBan(sender)) {
            sender.sendMessage("§cYou cannot ban players.");
            return true;
        }
        try {
            long duration = DurationParser.parseSeconds(args[1]);
            PermissionLimits.validateBanDuration(sender, duration);
            return applyBan(sender, args[0], duration, joinReason(args, 2), false);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("§c" + exception.getMessage());
            return true;
        }
    }

    private boolean handlePermanentBan(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /ban <player> [reason]");
            return true;
        }
        if (!sender.hasPermission("blade.ban.permanent") && !sender.hasPermission("blade.admin")) {
            sender.sendMessage("§cYou cannot permanently ban players.");
            return true;
        }
        return applyBan(sender, args[0], -1L, joinReason(args, 1), false);
    }

    private boolean applyBan(CommandSender sender, String targetName, long durationSeconds, String reason, boolean ipBan) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String ip = target.isOnline() && target.getPlayer() != null && target.getPlayer().getAddress() != null
                ? target.getPlayer().getAddress().getAddress().getHostAddress()
                : "";
        if (ipBan) {
            if (!sender.hasPermission("blade.ban.ip") && !sender.hasPermission("blade.admin")) {
                sender.sendMessage("§cYou cannot IP-ban players.");
                return true;
            }
            if (ip.isBlank()) {
                sender.sendMessage("§cPlayer must be online to IP-ban.");
                return true;
            }
            punishmentManager.ipBan(
                    target.getUniqueId(),
                    target.getName() == null ? targetName : target.getName(),
                    sender.getName(),
                    reason,
                    ip
            );
        } else {
            punishmentManager.ban(
                    target.getUniqueId(),
                    target.getName() == null ? targetName : target.getName(),
                    sender.getName(),
                    reason,
                    durationSeconds,
                    ip
            );
        }
        sender.sendMessage("§aBanned §f" + targetName + " §afor §f" + DurationParser.formatSeconds(durationSeconds) + "§a.");
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.kickPlayer("§cYou are banned from Blade.\n§7Reason: §f" + reason);
        }
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /unmute <player>");
            return true;
        }
        if (!sender.hasPermission("blade.unmute") && !sender.hasPermission("blade.admin")) {
            sender.sendMessage("§cYou cannot unmute players.");
            return true;
        }
        UUID uuid = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
        if (punishmentManager.unmute(uuid)) {
            sender.sendMessage("§aUnmuted §f" + args[0] + "§a.");
        } else {
            sender.sendMessage("§cThat player is not muted.");
        }
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /unban <player>");
            return true;
        }
        if (!sender.hasPermission("blade.unban") && !sender.hasPermission("blade.admin")) {
            sender.sendMessage("§cYou cannot unban players.");
            return true;
        }
        UUID uuid = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
        PunishmentRecord ban = punishmentManager.getActiveBan(uuid, null);
        if (ban != null && ban.isPermanent()
                && !sender.hasPermission("blade.admin")
                && !sender.hasPermission("blade.ban.permanent")) {
            sender.sendMessage("§cYou cannot unban permanent bans.");
            return true;
        }
        if (punishmentManager.unban(uuid)) {
            sender.sendMessage("§aUnbanned §f" + args[0] + "§a.");
        } else {
            sender.sendMessage("§cThat player is not banned.");
        }
        return true;
    }

    private boolean handleIpBan(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /ipban <player> [reason]");
            return true;
        }
        return applyBan(sender, args[0], -1L, joinReason(args, 1), true);
    }

    private boolean handleRank(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /rank <player> <group>");
            return true;
        }
        rankManager.setRank(sender, args[0], args[1]);
        return true;
    }

    private boolean handleBladeOp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /bladeop <player>");
            return true;
        }
        rankManager.grantOperator(sender, args[0]);
        return true;
    }

    private String joinReason(String[] args, int start) {
        if (start >= args.length) {
            return "No reason";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
