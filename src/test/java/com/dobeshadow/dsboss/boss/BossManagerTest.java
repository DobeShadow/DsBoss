package com.dobeshadow.dsboss.boss;

import com.dobeshadow.dsboss.DsBoss;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossManagerTest {

    @Test
    void shouldColorizeTextWithMinecraftColorCodes() {
        String colored = BossManager.colorize("&cHello &aWorld");

        assertEquals(ChatColor.COLOR_CHAR + "cHello " + ChatColor.COLOR_CHAR + "aWorld", colored);
    }

    @Test
    void shouldReturnEmptyStringForNullColorizationInput() {
        assertEquals("", BossManager.colorize(null));
    }

    @Test
    void shouldExposeActiveBossStateThroughInnerClass() {
        BossManager.ActiveBoss activeBoss = new BossManager.ActiveBoss(null, null, null);

        assertEquals(null, activeBoss.config());
        assertEquals(null, activeBoss.entity());
        assertEquals(null, activeBoss.bossBar());
        assertTrue(activeBoss.participants().isEmpty());
    }
}
