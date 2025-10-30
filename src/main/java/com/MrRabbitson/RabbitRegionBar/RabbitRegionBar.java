package com.MrRabbitson.RabbitRegionBar;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class RabbitRegionBar extends JavaPlugin {

    private Map<String, RegionSettings> regionSettings;
    private Map<String, String> messages;
    private Map<String, BarColor> defaultColors;
    private final Map<Player, BossBar> bossBars = new HashMap<>();
    private boolean prioritizeHeight;

    private static class RegionSettings {
        String displayName;
        BarColor color;

        RegionSettings(String displayName, BarColor color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            getLogger().severe("WorldGuard не найден! Плагин будет отключен.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateBossBar(player);
                }
            }
        }.runTaskTimer(this, 0, getConfig().getInt("update-interval", 20));

        getLogger().info("RabbitRegionBar успешно запущен!");
    }

    @Override
    public void onDisable() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
        getLogger().info("RabbitRegionBar успешно выключен!");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        // загрузка конфига
        regionSettings = new HashMap<>();
        ConfigurationSection regionsSection = config.getConfigurationSection("regions");
        if (regionsSection != null) {
            for (String regionId : regionsSection.getKeys(false)) {
                String displayName = regionsSection.getString(regionId + ".name");
                BarColor color = getBarColor(regionsSection.getString(regionId + ".color"));
                regionSettings.put(regionId.toLowerCase(), new RegionSettings(displayName, color));
            }
        }

        // загрузка сообщений из конфига
        messages = new HashMap<>();
        messages.put("free-territory", config.getString("messages.free-territory", "§aСвободная территория"));
        messages.put("your-territory", config.getString("messages.your-territory", "§9Ваша территория"));
        messages.put("occupied-territory", config.getString("messages.occupied-territory", "§cЗанятая территория"));

        // цвета
        defaultColors = new HashMap<>();
        defaultColors.put("free", getBarColor(config.getString("default-colors.free", "GREEN")));
        defaultColors.put("your", getBarColor(config.getString("default-colors.your", "BLUE")));
        defaultColors.put("occupied", getBarColor(config.getString("default-colors.occupied", "RED")));

        prioritizeHeight = config.getBoolean("display.prioritize-height", true);
    }

    private BarColor getBarColor(String colorName) {
        try {
            return BarColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Неизвестный цвет: " + colorName + ". Использую WHITE");
            return BarColor.WHITE;
        }
    }

    private void updateBossBar(Player player) {
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));
        if (regionManager == null) return;

        ProtectedRegion region = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation())).getRegions().stream().findFirst().orElse(null);

        String message;
        BarColor color;

        if (region == null) {
            message = messages.get("free-territory");
            color = defaultColors.get("free");
        } else {
            String regionId = region.getId().toLowerCase();
            RegionSettings settings = regionSettings.get(regionId);

            if (settings != null) {
                message = settings.displayName;
                color = settings.color;
            } else if (region.getOwners().contains(player.getUniqueId()) || region.getMembers().contains(player.getUniqueId())) {
                message = messages.get("your-territory");
                color = defaultColors.get("your");
            } else {
                message = messages.get("occupied-territory");
                color = defaultColors.get("occupied");
            }
        }

        BossBar bossBar = bossBars.computeIfAbsent(player,
                p -> Bukkit.createBossBar(message, color, BarStyle.SOLID));

        bossBar.setTitle(message);
        bossBar.setColor(color);
        bossBar.setProgress(1.0);

        if (prioritizeHeight) {
            bossBar.setVisible(true);
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .forEach(bossBar::removePlayer);
        }

        if (!bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        saveDefaultConfig();
        loadConfig();
    }

}
