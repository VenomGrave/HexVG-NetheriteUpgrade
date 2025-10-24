package com.venom.hexplugin.hexupgrade;

import com.venom.hexplugin.hexupgrade.economy.VaultHook;
import com.venom.hexplugin.hexupgrade.gui.GuiManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class HexVGNetheriteUpgrade extends JavaPlugin {

    private static HexVGNetheriteUpgrade instance;
    private VaultHook vaultHook;
    private FileConfiguration messages;

    public static HexVGNetheriteUpgrade get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        loadMessages();

        vaultHook = new VaultHook();
        if (getConfig().getBoolean("economy.enabled", true)) {
            if (!vaultHook.setupEconomy()) {
                getLogger().warning("Vault/Economy not found! Economy costs won't work.");
            } else {
                getLogger().info("Vault economy hooked: " + vaultHook.getEconomy().getName());
            }
        }

        Bukkit.getPluginManager().registerEvents(new GuiManager(this), this);
        getLogger().info("HexVG-NetheriteUpgrade enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("HexVG-NetheriteUpgrade disabled.");
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public void reloadAll() {
        reloadConfig();
        loadMessages();
    }

    private void loadMessages() {
        File msgFile = new File(getDataFolder(), "messages.yml");
        if (msgFile.exists()) {
            messages = YamlConfiguration.loadConfiguration(msgFile);
        } else {
            messages = YamlConfiguration.loadConfiguration(msgFile);
            if (getConfig().isConfigurationSection("messages")) {
                for (String key : getConfig().getConfigurationSection("messages").getKeys(true)) {
                    messages.set(key, getConfig().get("messages." + key));
                }
            }
        }
    }
}
