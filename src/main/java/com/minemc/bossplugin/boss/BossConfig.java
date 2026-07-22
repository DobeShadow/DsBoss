package com.minemc.bossplugin.boss;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POJO representing a single boss configuration loaded from config.yml.
 */
public class BossConfig {

    private final String id;
    private final boolean enabled;
    private final String entityTypeName;
    private final String displayName;
    private final double health;
    private final double damage;
    private final String worldName;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final List<String> spawnTimes;
    private final boolean fireImmune;
    private final boolean fallImmune;

    // Equipment
    private final String helmet;
    private final String chestplate;
    private final String leggings;
    private final String boots;
    private final String mainHand;
    private final String offHand;

    // Potion effects
    private final List<PotionEffect> potionEffects;

    // Last-hit rewards
    private final double lastHitEconomy;
    private final List<String> lastHitCommands;
    private final List<String> lastHitMessages;

    // Server-wide rewards
    private final double serverEconomy;
    private final List<String> serverCommands;
    private final List<String> serverBroadcasts;

    // Drops
    private final List<DropConfig> drops;

    // Broadcasts
    private final List<String> broadcastOnSpawn;
    private final List<String> broadcastOnDeath;

    public BossConfig(String id, ConfigurationSection section, Logger logger) {
        this.id = id;
        this.enabled = section.getBoolean("enabled", true);
        this.entityTypeName = section.getString("type", "ZOMBIE");
        this.displayName = section.getString("name", "&c&lBOSS");
        this.health = section.getDouble("health", 100.0);
        this.damage = section.getDouble("damage", 5.0);
        this.worldName = section.getString("spawn-location.world", "world");
        this.spawnX = section.getDouble("spawn-location.x", 0.0);
        this.spawnY = section.getDouble("spawn-location.y", 64.0);
        this.spawnZ = section.getDouble("spawn-location.z", 0.0);
        this.spawnTimes = section.getStringList("spawn-times");
        this.fireImmune = section.getBoolean("fire-immune", true);
        this.fallImmune = section.getBoolean("fall-immune", true);

        // Equipment
        ConfigurationSection equip = section.getConfigurationSection("equipment");
        if (equip != null) {
            this.helmet = equip.getString("helmet", "");
            this.chestplate = equip.getString("chestplate", "");
            this.leggings = equip.getString("leggings", "");
            this.boots = equip.getString("boots", "");
            this.mainHand = equip.getString("main-hand", "");
            this.offHand = equip.getString("off-hand", "");
        } else {
            this.helmet = "";
            this.chestplate = "";
            this.leggings = "";
            this.boots = "";
            this.mainHand = "";
            this.offHand = "";
        }

        // Potion effects
        this.potionEffects = new ArrayList<>();
        List<Map<?, ?>> effectList = section.getMapList("potion-effects");
        for (Map<?, ?> effectMap : effectList) {
            try {
                String typeName = (String) effectMap.get("type");
                int level = effectMap.get("level") instanceof Number n ? n.intValue() : 1;
                int duration = effectMap.get("duration") instanceof Number n ? n.intValue() : 999999;
                PotionEffectType effectType = PotionEffectType.getByName(typeName.toUpperCase());
                if (effectType != null) {
                    potionEffects.add(new PotionEffect(effectType, duration * 20, level - 1, false, true));
                } else {
                    logger.log(Level.WARNING, "Boss '{0}': Unknown potion effect type '{1}'", new Object[]{id, typeName});
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Boss '" + id + "': Failed to parse potion effect: " + effectMap, e);
            }
        }

        // Last-hit rewards
        ConfigurationSection lhr = section.getConfigurationSection("last-hit-rewards");
        if (lhr != null) {
            this.lastHitEconomy = lhr.getDouble("economy", 0.0);
            this.lastHitCommands = lhr.getStringList("commands");
            this.lastHitMessages = lhr.getStringList("messages");
        } else {
            this.lastHitEconomy = 0.0;
            this.lastHitCommands = new ArrayList<>();
            this.lastHitMessages = new ArrayList<>();
        }

        // Server rewards
        ConfigurationSection sr = section.getConfigurationSection("server-rewards");
        if (sr != null) {
            this.serverEconomy = sr.getDouble("economy", 0.0);
            this.serverCommands = sr.getStringList("commands");
            this.serverBroadcasts = sr.getStringList("broadcast");
        } else {
            this.serverEconomy = 0.0;
            this.serverCommands = new ArrayList<>();
            this.serverBroadcasts = new ArrayList<>();
        }

        // Drops
        this.drops = new ArrayList<>();
        List<Map<?, ?>> dropList = section.getMapList("drops");
        for (Map<?, ?> dropMap : dropList) {
            try {
                String matName = (String) dropMap.get("material");
                int amount = dropMap.get("amount") instanceof Number n ? n.intValue() : 1;
                double chance = dropMap.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
                Material material = Material.getMaterial(matName.toUpperCase());
                if (material != null) {
                    drops.add(new DropConfig(material, amount, chance));
                } else {
                    logger.log(Level.WARNING, "Boss '{0}': Unknown drop material '{1}'", new Object[]{id, matName});
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Boss '" + id + "': Failed to parse drop: " + dropMap, e);
            }
        }

        // Broadcasts
        this.broadcastOnSpawn = section.getStringList("broadcast-on-spawn");
        this.broadcastOnDeath = section.getStringList("broadcast-on-death");
    }

    // ---- Getters ----

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public String getEntityTypeName() { return entityTypeName; }
    public String getDisplayName() { return displayName; }
    public double getHealth() { return health; }
    public double getDamage() { return damage; }
    public String getWorldName() { return worldName; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public List<String> getSpawnTimes() { return spawnTimes; }
    public boolean isFireImmune() { return fireImmune; }
    public boolean isFallImmune() { return fallImmune; }
    public String getHelmet() { return helmet; }
    public String getChestplate() { return chestplate; }
    public String getLeggings() { return leggings; }
    public String getBoots() { return boots; }
    public String getMainHand() { return mainHand; }
    public String getOffHand() { return offHand; }
    public List<PotionEffect> getPotionEffects() { return potionEffects; }
    public double getLastHitEconomy() { return lastHitEconomy; }
    public List<String> getLastHitCommands() { return lastHitCommands; }
    public List<String> getLastHitMessages() { return lastHitMessages; }
    public double getServerEconomy() { return serverEconomy; }
    public List<String> getServerCommands() { return serverCommands; }
    public List<String> getServerBroadcasts() { return serverBroadcasts; }
    public List<DropConfig> getDrops() { return drops; }
    public List<String> getBroadcastOnSpawn() { return broadcastOnSpawn; }
    public List<String> getBroadcastOnDeath() { return broadcastOnDeath; }

    // ---- DropConfig inner record ----

    public record DropConfig(Material material, int amount, double chance) {}

    @Override
    public String toString() {
        return "BossConfig{id='" + id + "', name='" + displayName + "', type=" + entityTypeName
                + ", health=" + health + ", times=" + spawnTimes + ", enabled=" + enabled + "}";
    }
}
