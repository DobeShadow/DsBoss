package com.minemc.bossplugin.listener;

import com.minemc.bossplugin.CustomBoss;
import com.minemc.bossplugin.boss.BossManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles boss events: death/rewards, damage/participant tracking, immunities, boss bar.
 */
public class BossListener implements Listener {

    private final CustomBoss plugin;
    private final BossManager bossManager;

    public BossListener(CustomBoss plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    /**
     * Track participants and update boss bar on damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!isBoss(entity)) return;

        UUID uuid = entity.getUniqueId();
        BossManager.ActiveBoss ab = bossManager.getActiveBosses().get(uuid);
        if (ab == null) return;

        // Update boss bar
        plugin.getServer().getScheduler().runTask(plugin, bossManager::updateBossBars);

        // Track participant and show boss bar
        if (event instanceof EntityDamageByEntityEvent ede) {
            if (ede.getDamager() instanceof Player player) {
                ab.addParticipant(player.getUniqueId());
                if (ab.bossBar() != null) {
                    ab.bossBar().addPlayer(player);
                }
            }
        }
    }

    /**
     * Handle boss death — trigger rewards and broadcasts.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isBoss(entity)) return;

        Player killer = entity.getKiller();
        bossManager.onBossDeath(entity, killer);

        // We handle drops ourselves (via participant tracking)
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /**
     * Fire immunity for bosses.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossCombust(EntityCombustEvent event) {
        if (!isBoss(event.getEntity())) return;

        UUID uuid = event.getEntity().getUniqueId();
        var ab = bossManager.getActiveBosses().get(uuid);
        if (ab != null && ab.config().isFireImmune()) {
            event.setCancelled(true);
        }
    }

    /**
     * Fall damage immunity for bosses.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossFallDamage(EntityDamageEvent event) {
        if (!isBoss(event.getEntity())) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        UUID uuid = event.getEntity().getUniqueId();
        var ab = bossManager.getActiveBosses().get(uuid);
        if (ab != null && ab.config().isFallImmune()) {
            event.setCancelled(true);
        }
    }

    private boolean isBoss(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        return entity.getPersistentDataContainer().has(plugin.getBossIdKey(), PersistentDataType.STRING);
    }
}
