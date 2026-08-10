package com.dobeshadow.dsboss;

import com.dobeshadow.dsboss.boss.BossManager;
import com.dobeshadow.dsboss.commands.BossCommand;
import com.dobeshadow.dsboss.listener.BossListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * DsBoss - Custom BOSS plugin for Paper/Spigot 1.21+
 *
 * Features:
 * - Configurable boss types, names, and health
 * - Custom drops and economy rewards (Vault)
 * - Last-hit bonus rewards
 * - Server-wide rewards on boss death
 * - Scheduled spawn times (Beijing time)
 * - Multi-line broadcast messages on spawn/death
 */
public final class DsBoss extends JavaPlugin {

    private static DsBoss instance;
    private BossManager bossManager;
    private Economy economy;
    private NamespacedKey bossIdKey;

    @Override
    public void onEnable() {
        instance = this;
        this.bossIdKey = new NamespacedKey(this, "boss_id");

        // Try to hook into Vault economy
        if (!setupEconomy()) {
            getLogger().log(Level.WARNING, "Vault或经济插件未找到！经济奖励功能将不可用，请使用命令奖励代替。");
        } else {
            getLogger().info("已连接到Vault经济系统: " + economy.getName());
        }

        // Initialize boss manager
        bossManager = new BossManager(this);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new BossListener(this, bossManager), this);

        // Register commands
        BossCommand cmd = new BossCommand(this, bossManager);
        getCommand("dsboss").setExecutor(cmd);
        getCommand("dsboss").setTabCompleter(cmd);

        // Load configurations (this also starts the scheduler)
        bossManager.load();

        getLogger().info("DsBoss v" + getPluginMeta().getVersion() + " 已启动！");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.cleanup();
        }
        instance = null;
        getLogger().info("DsBoss 已卸载！");
    }

    /**
     * Attempt to hook into Vault economy.
     */
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        try {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            economy = rsp.getProvider();
            return economy != null;
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to hook into Vault economy", e);
            return false;
        }
    }

    // ---- Static accessors ----

    public static DsBoss getInstance() {
        return instance;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    public NamespacedKey getBossIdKey() {
        return bossIdKey;
    }
}
