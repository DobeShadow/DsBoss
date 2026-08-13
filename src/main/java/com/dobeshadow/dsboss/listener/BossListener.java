package com.dobeshadow.dsboss.listener;

import com.dobeshadow.dsboss.DsBoss;
import com.dobeshadow.dsboss.boss.BossManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

    private final DsBoss plugin;
    private final BossManager bossManager;

    public BossListener(DsBoss plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    /**
     * Intercept damage for bosses with custom health (when server caps configured value).
     * Subtracts from custom health and scales the entity's visual health proportionally.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossCustomHealthDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!isBoss(entity)) return;

        UUID uuid = entity.getUniqueId();
        BossManager.ActiveBoss ab = bossManager.getActiveBosses().get(uuid);
        if (ab == null || !ab.useCustomHealth()) return;

        // Track the last player attacker so last-hit rewards work even though we kill via setHealth(0)
        if (event instanceof EntityDamageByEntityEvent ede) {
            if (ede.getDamager() instanceof Player player) {
                ab.setLastAttacker(player);
            }
        }

        double finalDamage = event.getFinalDamage();
        double newCustomHealth = Math.max(0, ab.getCustomHealth() - finalDamage);
        ab.setCustomHealth(newCustomHealth);

        if (newCustomHealth <= 0) {
            // Boss defeated — schedule death on next tick to avoid event interference
            event.setDamage(0);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!entity.isDead()) {
                    entity.setHealth(0);
                }
            });
        } else {
            // Scale visual entity health to reflect custom health percentage (keep at least 1 HP)
            double ratio = newCustomHealth / ab.config().getHealth();
            AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double entityMaxHealth = attr != null ? attr.getValue() : 1024.0;
            double newHealth = 1.0 + ratio * (entityMaxHealth - 1.0);
            event.setDamage(0);
            entity.setHealth(Math.min(entityMaxHealth, newHealth));
        }
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

        // Refresh last-damage timestamp so idle-based auto-despawn won't kill a fought boss
        ab.markDamaged();

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
        if (killer == null) {
            // Custom-health bosses die via setHealth(0), so getKiller() is null.
            // Fall back to the last recorded player attacker to grant last-hit rewards.
            BossManager.ActiveBoss ab = bossManager.getActiveBosses().get(entity.getUniqueId());
            if (ab != null) {
                killer = ab.getLastAttacker();
            }
        }
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
