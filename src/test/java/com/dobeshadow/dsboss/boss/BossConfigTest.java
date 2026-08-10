package com.dobeshadow.dsboss.boss;

import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BossConfigTest {

    @Test
    void shouldPopulateBasicFieldsFromConfigurationSection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", false);
        config.set("type", "ZOMBIE");
        config.set("name", "&cTest Boss");
        config.set("health", 250.0);
        config.set("damage", 12.5);
        config.set("spawn-location.world", "world");
        config.set("spawn-location.x", 1.0);
        config.set("spawn-location.y", 64.0);
        config.set("spawn-location.z", 3.0);
        config.set("spawn-times", List.of("12:00", "18:00"));
        config.set("fire-immune", true);
        config.set("fall-immune", false);

        BossConfig bossConfig = new BossConfig("test-boss", config, Logger.getLogger("test"));

        assertFalse(bossConfig.isEnabled());
        assertEquals("ZOMBIE", bossConfig.getEntityTypeName());
        assertEquals("&cTest Boss", bossConfig.getDisplayName());
        assertEquals(250.0, bossConfig.getHealth());
        assertEquals(12.5, bossConfig.getDamage());
        assertEquals("world", bossConfig.getWorldName());
        assertEquals(1.0, bossConfig.getSpawnX());
        assertEquals(64.0, bossConfig.getSpawnY());
        assertEquals(3.0, bossConfig.getSpawnZ());
        assertEquals(List.of("12:00", "18:00"), bossConfig.getSpawnTimes());
        assertTrue(bossConfig.isFireImmune());
        assertFalse(bossConfig.isFallImmune());
    }

    @Test
    void shouldParseRewardAndDropSectionsIntoCollections() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("last-hit-rewards.economy", 25.0);
        config.set("last-hit-rewards.commands", List.of("say hi"));
        config.set("last-hit-rewards.messages", List.of("boss dead"));
        config.set("server-rewards.economy", 50.0);
        config.set("server-rewards.commands", List.of("broadcast hello"));
        config.set("server-rewards.broadcast", List.of("server msg"));
        config.createSection("drops");
        MemorySection dropsSection = (MemorySection) config.get("drops");
        YamlConfiguration dropsConfig = new YamlConfiguration();
        dropsConfig.set("material", "DIAMOND");
        dropsConfig.set("amount", 3);
        dropsConfig.set("chance", 0.5);
        dropsSection.createSection("0", dropsConfig.getValues(false));
        config.set("drops", dropsSection.getValues(false));

        BossConfig bossConfig = new BossConfig("reward-boss", config, Logger.getLogger("test"));

        assertEquals(25.0, bossConfig.getLastHitEconomy());
        assertEquals(List.of("say hi"), bossConfig.getLastHitCommands());
        assertEquals(List.of("boss dead"), bossConfig.getLastHitMessages());
        assertEquals(50.0, bossConfig.getServerEconomy());
        assertEquals(List.of("broadcast hello"), bossConfig.getServerCommands());
        assertEquals(List.of("server msg"), bossConfig.getServerBroadcasts());
        assertEquals(1, bossConfig.getDrops().size());
        assertEquals("DIAMOND", bossConfig.getDrops().get(0).material().name());
        assertEquals(3, bossConfig.getDrops().get(0).amount());
        assertEquals(0.5, bossConfig.getDrops().get(0).chance());
    }
}
