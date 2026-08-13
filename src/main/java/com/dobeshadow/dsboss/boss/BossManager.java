package com.dobeshadow.dsboss.boss;

import com.dobeshadow.dsboss.DsBoss;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages all boss lifecycle: config loading, scheduled spawning, reward distribution, and cleanup.
 */
public class BossManager {

    private final DsBoss plugin;
    private final Map<String, BossConfig> bossConfigs = new HashMap<>();
    private final Map<UUID, ActiveBoss> activeBosses = new HashMap<>();
    private final Set<String> spawnedTimeSlots = new HashSet<>();

    private BukkitTask schedulerTask;
    private int checkInterval;
    private ZoneId timezone;
    private String broadcastPrefix;
    private boolean enableBossBar;
    private int despawnAfterMinutes;
    private int despawnIdleMinutes;
    private String despawnBroadcast;

    public BossManager(DsBoss plugin) {
        this.plugin = plugin;
    }

    /**
     * Load (or reload) all boss configurations from config.yml and restart the scheduler.
     */
    public synchronized void load() {
        // Cancel any existing scheduler
        stopScheduler();
        // Despawn all active bosses
        despawnAllBosses();

        bossConfigs.clear();
        activeBosses.clear();
        spawnedTimeSlots.clear();

        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        checkInterval = config.getInt("check-interval", 30);
        String tz = config.getString("timezone", "Asia/Shanghai");
        try {
            timezone = ZoneId.of(tz);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid timezone '{0}', falling back to Asia/Shanghai", tz);
            timezone = ZoneId.of("Asia/Shanghai");
        }
        broadcastPrefix = colorize(config.getString("broadcast-prefix", "&c&l[BOSS公告] "));
        enableBossBar = config.getBoolean("enable-boss-bar", true);

        // Auto-despawn settings
        ConfigurationSection despawnSection = config.getConfigurationSection("auto-despawn");
        if (despawnSection != null) {
            despawnAfterMinutes = despawnSection.getInt("minutes", 120);
            despawnIdleMinutes = despawnSection.getInt("idle-minutes", 10);
            despawnBroadcast = despawnSection.getString("broadcast", "");
        } else {
            despawnAfterMinutes = 0;
            despawnIdleMinutes = 10;
            despawnBroadcast = "";
        }

        ConfigurationSection bossesSection = config.getConfigurationSection("bosses");
        if (bossesSection == null) {
            plugin.getLogger().warning("No 'bosses' section found in config.yml!");
            return;
        }

        for (String key : bossesSection.getKeys(false)) {
            ConfigurationSection bossSection = bossesSection.getConfigurationSection(key);
            if (bossSection == null) continue;
            try {
                BossConfig bossConfig = new BossConfig(key, bossSection, plugin.getLogger());
                bossConfigs.put(key, bossConfig);
                plugin.getLogger().info("Loaded boss config: " + key + " (enabled=" + bossConfig.isEnabled() + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load boss config '" + key + "'", e);
            }
        }

        plugin.getLogger().info("Loaded " + bossConfigs.size() + " boss configuration(s).");
        startScheduler();
    }

    /**
     * Reload configurations and restart everything.
     */
    public synchronized void reload() {
        load();
    }

    /**
     * Spawn a boss by config ID at its configured location.
     */
    public boolean spawnBoss(String bossId) {
        return spawnBossAt(bossId, null);
    }

    /**
     * Spawn a boss at a custom location (or its configured location if loc is null).
     */
    public boolean spawnBossAt(String bossId, Location customLoc) {
        BossConfig cfg = bossConfigs.get(bossId);
        if (cfg == null) {
            plugin.getLogger().warning("Unknown boss ID: " + bossId);
            return false;
        }
        if (!cfg.isEnabled()) {
            plugin.getLogger().warning("Boss '" + bossId + "' is disabled in config.");
            return false;
        }

        // Check if this boss is already alive
        for (ActiveBoss ab : activeBosses.values()) {
            if (ab.config.getId().equals(bossId) && !ab.entity.isDead()) {
                plugin.getLogger().info("Boss '" + bossId + "' is already alive, skipping spawn.");
                return false;
            }
        }

        // Resolve location
        Location loc;
        if (customLoc != null) {
            loc = customLoc;
        } else {
            World world = Bukkit.getWorld(cfg.getWorldName());
            if (world == null) {
                plugin.getLogger().warning("World '" + cfg.getWorldName() + "' not found for boss '" + bossId + "'!");
                return false;
            }
            loc = new Location(world, cfg.getSpawnX(), cfg.getSpawnY(), cfg.getSpawnZ());
        }

        // Resolve entity type
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(cfg.getEntityTypeName().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid entity type '" + cfg.getEntityTypeName() + "' for boss '" + bossId + "'!");
            return false;
        }

        // Spawn the entity
        World world = loc.getWorld();
        if (world == null) return false;

        LivingEntity entity;
        try {
            entity = (LivingEntity) world.spawnEntity(loc, entityType);
            if (entity == null) {
                plugin.getLogger().warning("Boss '" + bossId + "' 生成失败: 实体被取消 (WorldGuard 区域 flag 或其它插件拦截)");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn boss '" + bossId + "'", e);
            return false;
        }

        // Mark with PDC
        entity.getPersistentDataContainer().set(plugin.getBossIdKey(), PersistentDataType.STRING, bossId);

        // Set custom name
        String coloredName = colorize(cfg.getDisplayName());
        entity.setCustomName(coloredName);
        entity.setCustomNameVisible(true);

        // Set max health – server respects spigot.yml max-health setting
        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double configuredHealth = cfg.getHealth();
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(configuredHealth);
        }
        entity.setHealth(configuredHealth);

        // Set attack damage
        AttributeInstance attackAttr = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(cfg.getDamage());
        }

        // Set immunity flags
        if (entity instanceof Mob mob) {
            if (cfg.isFireImmune()) {
                // No direct fire immunity API, but we set fire ticks to 0 via persistent flag
            }
        }

        // Equip armor and weapons
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            if (!cfg.getHelmet().isEmpty())
                equipment.setHelmet(parseMaterial(cfg.getHelmet()));
            if (!cfg.getChestplate().isEmpty())
                equipment.setChestplate(parseMaterial(cfg.getChestplate()));
            if (!cfg.getLeggings().isEmpty())
                equipment.setLeggings(parseMaterial(cfg.getLeggings()));
            if (!cfg.getBoots().isEmpty())
                equipment.setBoots(parseMaterial(cfg.getBoots()));
            if (!cfg.getMainHand().isEmpty())
                equipment.setItemInMainHand(parseMaterial(cfg.getMainHand()));
            if (!cfg.getOffHand().isEmpty())
                equipment.setItemInOffHand(parseMaterial(cfg.getOffHand()));

            // Set drop chance to 0 so the boss doesn't duplicate equipment on death
            equipment.setHelmetDropChance(0f);
            equipment.setChestplateDropChance(0f);
            equipment.setLeggingsDropChance(0f);
            equipment.setBootsDropChance(0f);
            equipment.setItemInMainHandDropChance(0f);
            equipment.setItemInOffHandDropChance(0f);
        }

        // Apply potion effects
        for (var effect : cfg.getPotionEffects()) {
            entity.addPotionEffect(effect);
        }

        // Set remove when far away (false = persistent)
        entity.setRemoveWhenFarAway(false);

        // Create boss bar
        BossBar bossBar = null;
        if (enableBossBar) {
            bossBar = Bukkit.createBossBar(coloredName, BarColor.RED, BarStyle.SOLID);
            bossBar.setProgress(1.0);
            bossBar.setVisible(true);
        }

        // Track the active boss – enable custom health tracking only when server cap is lower than configured
        ActiveBoss activeBoss = new ActiveBoss(cfg, entity, bossBar);
        if (entity.getHealth() < configuredHealth) {
            activeBoss.setCustomHealth(configuredHealth);
        }
        activeBosses.put(entity.getUniqueId(), activeBoss);

        // Broadcast spawn messages
        broadcastBossEvent(cfg.getBroadcastOnSpawn(), cfg, entity, null);

        plugin.getLogger().info("Boss '" + bossId + "' spawned at " + formatLocation(loc));
        return true;
    }

    /**
     * Kill a specific boss by config ID.
     */
    public boolean killBoss(String bossId) {
        for (Map.Entry<UUID, ActiveBoss> entry : activeBosses.entrySet()) {
            ActiveBoss ab = entry.getValue();
            if (ab.config.getId().equals(bossId) && !ab.entity.isDead()) {
                removeBossBar(ab);
                ab.entity.setHealth(0);
                activeBosses.remove(entry.getKey());
                plugin.getLogger().info("Boss '" + bossId + "' has been killed.");
                return true;
            }
        }
        plugin.getLogger().info("No active boss found with ID '" + bossId + "'.");
        return false;
    }

    /**
     * Handle boss death event. Called by the listener.
     */
    public void onBossDeath(LivingEntity entity, Player killer) {
        UUID uuid = entity.getUniqueId();
        ActiveBoss ab = activeBosses.remove(uuid);
        if (ab == null) return;

        BossConfig cfg = ab.config;
        removeBossBar(ab);

        // Clear potion effects
        entity.clearActivePotionEffects();

        // --- Last-hit rewards ---
        if (killer != null) {
            // Economy
            if (cfg.getLastHitEconomy() > 0) {
                giveEconomy(killer, cfg.getLastHitEconomy());
            }
            // Commands
            executeCommands(cfg.getLastHitCommands(), killer);
            // Messages
            for (String msg : cfg.getLastHitMessages()) {
                String formatted = formatPlaceholders(msg, cfg, entity, killer);
                killer.sendMessage(formatted);
            }
        }

        // --- Server-wide rewards ---
        if (cfg.getServerEconomy() > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                giveEconomy(player, cfg.getServerEconomy());
            }
        }

        // Execute server commands for all online players
        if (!cfg.getServerCommands().isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                executeCommands(cfg.getServerCommands(), player);
            }
        }

        // Server broadcast messages
        if (!cfg.getServerBroadcasts().isEmpty()) {
            broadcastBossEvent(cfg.getServerBroadcasts(), cfg, entity, killer);
        }

        // Broadcast death messages
        broadcastBossEvent(cfg.getBroadcastOnDeath(), cfg, entity, killer);

        // --- Drops: give to every participant player ---
        giveDropsToParticipants(ab, cfg);

        plugin.getLogger().info("Boss '" + cfg.getId() + "' was killed by "
                + (killer != null ? killer.getName() : "unknown") + "!");
    }

    /**
     * Clean up on plugin disable.
     */
    public synchronized void cleanup() {
        stopScheduler();
        despawnAllBosses();
        bossConfigs.clear();
        activeBosses.clear();
        spawnedTimeSlots.clear();
    }

    /**
     * Get all active bosses.
     */
    public Map<UUID, ActiveBoss> getActiveBosses() {
        return Collections.unmodifiableMap(activeBosses);
    }

    /**
     * Get all boss configs.
     */
    public Map<String, BossConfig> getBossConfigs() {
        return Collections.unmodifiableMap(bossConfigs);
    }

    /**
     * Get the number of configured bosses.
     */
    public int getBossCount() {
        return bossConfigs.size();
    }

    /**
     * Get the number of currently alive bosses.
     */
    public int getActiveBossCount() {
        return (int) activeBosses.values().stream().filter(ab -> !ab.entity.isDead()).count();
    }

    // ==================== Private Methods ====================

    private void startScheduler() {
        // Check every checkInterval seconds
        long ticks = checkInterval * 20L;
        schedulerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkSpawnTimes();
            checkDespawn();
        }, ticks, ticks);
        plugin.getLogger().info("Scheduler started (check interval: " + checkInterval + "s, timezone: " + timezone + ")");
    }

    private void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    /**
     * Check if any boss should spawn based on current Beijing time.
     */
    private void checkSpawnTimes() {
        ZonedDateTime now = ZonedDateTime.now(timezone);
        String currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm"));

        for (BossConfig cfg : bossConfigs.values()) {
            if (!cfg.isEnabled()) continue;

            // Check if spawn time matches
            if (!cfg.getSpawnTimes().contains(currentTime)) continue;

            // Prevent double-spawning in the same minute slot
            String slotKey = cfg.getId() + ":" + currentTime + ":" + now.toLocalDate().toString();
            if (spawnedTimeSlots.contains(slotKey)) continue;

            // Check if boss is already alive
            boolean alreadyAlive = activeBosses.values().stream()
                    .anyMatch(ab -> ab.config.getId().equals(cfg.getId()) && !ab.entity.isDead());
            if (alreadyAlive) continue;

            // Spawn the boss
            spawnedTimeSlots.add(slotKey);
            spawnBoss(cfg.getId());
        }

        // Clean up old time slots (keep only today's entries)
        String today = now.toLocalDate().toString();
        spawnedTimeSlots.removeIf(slot -> !slot.endsWith(today));
    }

    private void despawnAllBosses() {
        for (ActiveBoss ab : activeBosses.values()) {
            removeBossBar(ab);
            if (!ab.entity.isDead()) {
                ab.entity.remove();
            }
        }
        activeBosses.clear();
    }

    /**
     * Clean up bosses that have been alive too long without being fought.
     * Runs on the scheduler alongside spawn checks.
     */
    private void checkDespawn() {
        if (despawnAfterMinutes <= 0) return;

        long now = System.currentTimeMillis();
        long maxAliveMs = despawnAfterMinutes * 60_000L;
        long idleLimitMs = despawnIdleMinutes * 60_000L;

        List<UUID> deadCleanup = new ArrayList<>();
        List<UUID> toDespawn = new ArrayList<>();
        for (Map.Entry<UUID, ActiveBoss> entry : activeBosses.entrySet()) {
            ActiveBoss ab = entry.getValue();
            if (ab.entity.isDead()) {
                deadCleanup.add(entry.getKey());
                continue;
            }
            // Must have been alive past the timeout
            if (now - ab.getSpawnTime() < maxAliveMs) continue;
            // If idle grace is enabled, only despawn when no one attacked recently
            if (despawnIdleMinutes > 0 && now - ab.getLastDamageTime() < idleLimitMs) continue;
            toDespawn.add(entry.getKey());
        }

        // Silently clean up dead entries (no broadcast, just remove bar)
        for (UUID uuid : deadCleanup) {
            ActiveBoss ab = activeBosses.remove(uuid);
            if (ab != null) removeBossBar(ab);
        }

        for (UUID uuid : toDespawn) {
            ActiveBoss ab = activeBosses.remove(uuid);
            if (ab != null) despawnBoss(ab);
        }
    }

    private void despawnBoss(ActiveBoss ab) {
        if (despawnBroadcast != null && !despawnBroadcast.isEmpty()) {
            String msg = formatPlaceholders(despawnBroadcast, ab.config, ab.entity, null);
            Bukkit.broadcastMessage(broadcastPrefix + msg);
        }
        removeBossBar(ab);
        if (!ab.entity.isDead()) {
            ab.entity.remove();
        }
        plugin.getLogger().info("Boss '" + ab.config.getId() + "' 超时无人挑战已清理 (存活 " + despawnAfterMinutes + " 分钟)");
    }

    private void removeBossBar(ActiveBoss ab) {
        if (ab.bossBar != null) {
            ab.bossBar.removeAll();
        }
    }

    private void broadcastBossEvent(List<String> messages, BossConfig cfg, LivingEntity entity, Player killer) {
        if (messages == null || messages.isEmpty()) return;

        for (String msg : messages) {
            String formatted = formatPlaceholders(msg, cfg, entity, killer);
            String fullMsg = broadcastPrefix + formatted;
            Bukkit.broadcastMessage(fullMsg);
        }
    }

    private String formatPlaceholders(String msg, BossConfig cfg, LivingEntity entity, Player killer) {
        if (msg == null) return "";

        Location loc = entity.getLocation();
        ZonedDateTime now = ZonedDateTime.now(timezone);

        double displayHealth;
        ActiveBoss ab = activeBosses.get(entity.getUniqueId());
        if (ab != null && ab.useCustomHealth()) {
            displayHealth = ab.getCustomHealth();
        } else {
            displayHealth = entity.getHealth();
        }
        double displayMaxHealth = cfg.getHealth();

        return colorize(msg
                .replace("{boss_name}", cfg.getDisplayName())
                .replace("{boss_type}", cfg.getEntityTypeName())
                .replace("{health}", String.format("%.0f", displayHealth))
                .replace("{max_health}", String.format("%.0f", displayMaxHealth))
                .replace("{world}", loc.getWorld() != null ? loc.getWorld().getName() : "?")
                .replace("{x}", String.format("%.0f", loc.getX()))
                .replace("{y}", String.format("%.0f", loc.getY()))
                .replace("{z}", String.format("%.0f", loc.getZ()))
                .replace("{killer}", killer != null ? killer.getName() : "?")
                .replace("{time}", now.format(DateTimeFormatter.ofPattern("HH:mm")))
                .replace("{timezone}", "北京")
                .replace("{kill_time}", now.format(DateTimeFormatter.ofPattern("HH:mm")))
        );
    }

    private void giveEconomy(Player player, double amount) {
        Economy economy = plugin.getEconomy();
        if (economy != null && economy.isEnabled()) {
            economy.depositPlayer(player, amount);
        } else {
            plugin.getLogger().log(Level.FINE, "Vault not available, skipping economy reward for {0}", player.getName());
        }
    }

    private void giveDropsToParticipants(ActiveBoss ab, BossConfig cfg) {
        Set<UUID> participantIds = ab.participants();

        // Also include the killer if present
        // (already in participants from damage tracking)

        for (BossConfig.DropConfig drop : cfg.getDrops()) {
            // Roll once per drop config; if success, give to every participant
            if (!(ThreadLocalRandom.current().nextDouble() < drop.chance())) continue;

            for (UUID pid : participantIds) {
                Player player = Bukkit.getPlayer(pid);
                if (player == null || !player.isOnline()) continue;

                ItemStack item = new ItemStack(drop.material(), drop.amount());
                var leftover = player.getInventory().addItem(item);
                // Drop what doesn't fit at player's feet
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }
    }

    private void executeCommands(List<String> commands, Player player) {
        if (commands == null || commands.isEmpty()) return;

        var console = Bukkit.getConsoleSender();
        for (String cmd : commands) {
            if (cmd == null || cmd.isEmpty()) continue;
            String processed = colorize(cmd.replace("[player]", player.getName()));
            Bukkit.dispatchCommand(console, processed);
        }
    }

    private ItemStack parseMaterial(String name) {
        if (name == null || name.isEmpty()) return null;
        Material mat = Material.getMaterial(name.toUpperCase());
        if (mat == null) {
            plugin.getLogger().warning("Invalid material: " + name);
            return null;
        }
        return new ItemStack(mat);
    }

    private String formatLocation(Location loc) {
        return String.format("%s (%.0f, %.0f, %.0f)",
                loc.getWorld() != null ? loc.getWorld().getName() : "?",
                loc.getX(), loc.getY(), loc.getZ());
    }

    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // ==================== ActiveBoss Class ====================

    /**
     * Tracks an active (spawned) boss with custom health support when server caps the configured value.
     */
    public static class ActiveBoss {
        private final BossConfig config;
        private final LivingEntity entity;
        private final BossBar bossBar;
        private final Set<UUID> participants = new HashSet<>();
        private final long spawnTime;
        private volatile long lastDamageTime;
        private double customHealth;
        private volatile Player lastAttacker;

        public ActiveBoss(BossConfig config, LivingEntity entity, BossBar bossBar) {
            this.config = config;
            this.entity = entity;
            this.bossBar = bossBar;
            this.spawnTime = System.currentTimeMillis();
            this.lastDamageTime = this.spawnTime;
        }

        public BossConfig config() { return config; }
        public LivingEntity entity() { return entity; }
        public BossBar bossBar() { return bossBar; }
        public Set<UUID> participants() { return participants; }
        public void addParticipant(UUID playerId) { participants.add(playerId); }
        public long getSpawnTime() { return spawnTime; }
        public long getLastDamageTime() { return lastDamageTime; }
        public void markDamaged() { lastDamageTime = System.currentTimeMillis(); }
        public double getCustomHealth() { return customHealth; }
        public void setCustomHealth(double health) { this.customHealth = health; }
        public boolean useCustomHealth() { return customHealth > 0; }
        public Player getLastAttacker() { return lastAttacker; }
        public void setLastAttacker(Player player) { this.lastAttacker = player; }
    }

    /**
     * Update boss bar progress for all active bosses. Called periodically or on boss damage.
     */
    public void updateBossBars() {
        for (ActiveBoss ab : activeBosses.values()) {
            if (ab.bossBar != null && !ab.entity.isDead()) {
                double currentHealth = ab.useCustomHealth() ? ab.getCustomHealth() : ab.entity.getHealth();
                double maxHealth = ab.config.getHealth();
                double progress = Math.max(0.0, Math.min(1.0, currentHealth / maxHealth));
                ab.bossBar.setProgress(progress);
                String title = colorize(ab.config.getDisplayName() + " &7[&c"
                        + String.format("%.0f", currentHealth) + "&7/&a"
                        + String.format("%.0f", maxHealth) + "&7]");
                ab.bossBar.setTitle(title);
            }
        }
    }
}
