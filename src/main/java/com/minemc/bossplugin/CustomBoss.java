package com.minemc.bossplugin;

import com.minemc.bossplugin.boss.BossManager;
import com.minemc.bossplugin.commands.BossCommand;
import com.minemc.bossplugin.listener.BossListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * CustomBoss - Custom BOSS plugin for Paper/Spigot 1.21+
 *
 * Features:
 * - Configurable boss types, names, and health
 * - Custom drops and economy rewards (Vault)
 * - Last-hit bonus rewards
 * - Server-wide rewards on boss death
 * - Scheduled spawn times (Beijing time)
 * - Multi-line broadcast messages on spawn/death
 */
public final class CustomBoss extends JavaPlugin {

    private static CustomBoss instance;
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
        getCommand("customboss").setExecutor(cmd);
        getCommand("customboss").setTabCompleter(cmd);

        // Load configurations (this also starts the scheduler)
        bossManager.load();

        getLogger().info("CustomBoss v" + getPluginMeta().getVersion() + " 已启动！");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.cleanup();
        }
        instance = null;
        getLogger().info("CustomBoss 已卸载！");
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

    public static CustomBoss getInstance() {
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
