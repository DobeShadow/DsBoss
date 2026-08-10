package com.dobeshadow.dsboss.commands;

import com.dobeshadow.dsboss.DsBoss;
import com.dobeshadow.dsboss.boss.BossConfig;
import com.dobeshadow.dsboss.boss.BossManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin command handler for DsBoss.
 * Subcommands: reload, list, spawn, kill, info
 */
public class BossCommand implements CommandExecutor, TabCompleter {

    private final DsBoss plugin;
    private final BossManager bossManager;

    private static final String PREFIX = "&8[&cDsBoss&8] &7";
    private static final String NO_PERM = "&c你没有权限使用此命令！";

    public BossCommand(DsBoss plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "spawn" -> handleSpawn(sender, args);
            case "kill" -> handleKill(sender, args);
            case "info" -> handleInfo(sender, args);
            default -> {
                sendMsg(sender, PREFIX + "&c未知子命令: &f/" + label + " " + sub);
                sendHelp(sender);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("dsboss.admin")) return List.of();

        if (args.length == 1) {
            return List.of("reload", "list", "spawn", "kill", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("spawn") || sub.equals("kill") || sub.equals("info")) {
                return bossManager.getBossConfigs().keySet().stream()
                        .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("spawn")) {
            // Suggest world names or coordinates
            if (args.length == 3) {
                return Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(w -> w.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            // For x, y, z suggest player's current position or just blank
            if (args.length == 4 || args.length == 5) {
                if (sender instanceof Player player) {
                    double coord = args.length == 4 ? player.getLocation().getX() : player.getLocation().getY();
                    return List.of(String.format("%.1f", coord));
                }
            }
        }

        return List.of();
    }

    private void handleReload(CommandSender sender) {
        if (!checkPerm(sender)) return;
        bossManager.reload();
        sendMsg(sender, PREFIX + "&a配置已重新加载！&7(共 " + bossManager.getBossCount() + " 个BOSS配置)");
    }

    private void handleList(CommandSender sender) {
        if (!checkPerm(sender)) return;
        sendMsg(sender, "&8&m-------------------------------");
        sendMsg(sender, "&c&lDsBoss &7- BOSS列表");
        sendMsg(sender, "&7活跃/总数: &c" + bossManager.getActiveBossCount() + "&7/&a" + bossManager.getBossCount());

        for (BossConfig cfg : bossManager.getBossConfigs().values()) {
            boolean isAlive = bossManager.getActiveBosses().values().stream()
                    .anyMatch(ab -> ab.config().getId().equals(cfg.getId()) && !ab.entity().isDead());
            String status = cfg.isEnabled()
                    ? (isAlive ? "&a● 存活" : "&7○ 未生成")
                    : "&8✕ 已禁用";

            sendMsg(sender, "  &e" + cfg.getId()
                    + " &7| " + status
                    + " &7| &f" + cfg.getDisplayName()
                    + " &7| 血量: &c" + String.format("%.0f", cfg.getHealth())
                    + " &7| 时间: &b" + String.join(", ", cfg.getSpawnTimes()));
        }
        sendMsg(sender, "&8&m-------------------------------");
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!checkPerm(sender)) return;

        if (args.length < 2) {
            sendMsg(sender, PREFIX + "&c用法: /boss spawn <BOSS_ID> [world x y z]");
            return;
        }

        String bossId = args[1];
        BossConfig cfg = bossManager.getBossConfigs().get(bossId);
        if (cfg == null) {
            sendMsg(sender, PREFIX + "&c未找到BOSS: " + bossId);
            return;
        }

        Location customLoc = null;
        if (args.length >= 6) {
            // /boss spawn <id> <world> <x> <y> <z>
            String worldName = args[2];
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sendMsg(sender, PREFIX + "&c世界不存在: " + worldName);
                return;
            }
            try {
                double x = Double.parseDouble(args[3]);
                double y = Double.parseDouble(args[4]);
                double z = Double.parseDouble(args[5]);
                customLoc = new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sendMsg(sender, PREFIX + "&c坐标格式错误！");
                return;
            }
        } else if (args.length == 5 || args.length == 3 || args.length == 4) {
            sendMsg(sender, PREFIX + "&c用法: /boss spawn <BOSS_ID> [world x y z]");
            return;
        }

        boolean success = bossManager.spawnBossAt(bossId, customLoc);
        if (success) {
            Location loc = customLoc != null ? customLoc
                    : new Location(Bukkit.getWorld(cfg.getWorldName()), cfg.getSpawnX(), cfg.getSpawnY(), cfg.getSpawnZ());
            sendMsg(sender, PREFIX + "&aBOSS '" + bossId + "' 已生成！ 位置: "
                    + loc.getWorld().getName() + " (" + String.format("%.0f", loc.getX())
                    + ", " + String.format("%.0f", loc.getY()) + ", " + String.format("%.0f", loc.getZ()) + ")");
        } else {
            sendMsg(sender, PREFIX + "&cBOSS生成失败，请检查控制台日志。");
        }
    }

    private void handleKill(CommandSender sender, String[] args) {
        if (!checkPerm(sender)) return;

        if (args.length < 2) {
            sendMsg(sender, PREFIX + "&c用法: /boss kill <BOSS_ID>");
            return;
        }

        String bossId = args[1];
        boolean success = bossManager.killBoss(bossId);
        if (success) {
            sendMsg(sender, PREFIX + "&aBOSS '" + bossId + "' 已被强制击杀！");
        } else {
            sendMsg(sender, PREFIX + "&c未找到活跃的BOSS: " + bossId);
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!checkPerm(sender)) return;

        if (args.length < 2) {
            sendMsg(sender, PREFIX + "&c用法: /boss info <BOSS_ID>");
            return;
        }

        String bossId = args[1];
        BossConfig cfg = bossManager.getBossConfigs().get(bossId);
        if (cfg == null) {
            sendMsg(sender, PREFIX + "&c未找到BOSS: " + bossId);
            return;
        }

        boolean isAlive = bossManager.getActiveBosses().values().stream()
                .anyMatch(ab -> ab.config().getId().equals(bossId) && !ab.entity().isDead());

        sendMsg(sender, "&8&m-------------------------------");
        sendMsg(sender, "&c&lBOSS信息: &e" + cfg.getId());
        sendMsg(sender, "&7名称: &f" + cfg.getDisplayName());
        sendMsg(sender, "&7类型: &f" + cfg.getEntityTypeName());
        sendMsg(sender, "&7血量: &c" + String.format("%.0f", cfg.getHealth()));
        sendMsg(sender, "&7攻击力: &c" + String.format("%.1f", cfg.getDamage()));
        sendMsg(sender, "&7状态: " + (cfg.isEnabled() ? (isAlive ? "&a● 存活" : "&7○ 未生成") : "&8✕ 已禁用"));
        sendMsg(sender, "&7生成时间: &b" + (cfg.getSpawnTimes().isEmpty() ? "无" : String.join(", ", cfg.getSpawnTimes())));
        sendMsg(sender, "&7生成位置: &f" + cfg.getWorldName() + " ("
                + String.format("%.0f", cfg.getSpawnX()) + ", "
                + String.format("%.0f", cfg.getSpawnY()) + ", "
                + String.format("%.0f", cfg.getSpawnZ()) + ")");
        sendMsg(sender, "&7最后一击金币: &6" + String.format("%.0f", cfg.getLastHitEconomy()));
        sendMsg(sender, "&7全服每人金币: &6" + String.format("%.0f", cfg.getServerEconomy()));
        sendMsg(sender, "&7掉落物数量: &e" + cfg.getDrops().size() + " 种");
        sendMsg(sender, "&8&m-------------------------------");
    }

    private void sendHelp(CommandSender sender) {
        if (!sender.hasPermission("dsboss.admin")) {
            sendMsg(sender, "&c你没有权限使用此命令！");
            return;
        }
        sendMsg(sender, "&8&m-------------------------------");
        sendMsg(sender, "&c&lDsBoss &7- 命令帮助");
        sendMsg(sender, "&e/boss reload &7- 重新加载配置");
        sendMsg(sender, "&e/boss list &7- 列出所有BOSS");
        sendMsg(sender, "&e/boss spawn <ID> [world x y z] &7- 生成BOSS");
        sendMsg(sender, "&e/boss kill <ID> &7- 强制击杀BOSS");
        sendMsg(sender, "&e/boss info <ID> &7- 查看BOSS详情");
        sendMsg(sender, "&8&m-------------------------------");
    }

    private boolean checkPerm(CommandSender sender) {
        if (!sender.hasPermission("dsboss.admin")) {
            sendMsg(sender, PREFIX + NO_PERM);
            return false;
        }
        return true;
    }

    private void sendMsg(CommandSender sender, String msg) {
        sender.sendMessage(BossManager.colorize(msg));
    }
}
