/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.config;

import dev.toxi.aurion2fa.Aurion2fa;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public final class ConfigManager {

    private final Aurion2fa plugin;

    private FileConfiguration config;
    private FileConfiguration pluginLocale;
    private FileConfiguration discordLocale;

    private final File configFile;
    private final File pluginLocaleFile;
    private final File discordLocaleFile;

    public ConfigManager(final Aurion2fa plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.pluginLocaleFile = new File(plugin.getDataFolder(), "plugin_locale.yml");
        this.discordLocaleFile = new File(plugin.getDataFolder(), "discord_locale.yml");
    }

    // Полная перезагрузка или первичная инициализация всех конфигов
    public void reloadConfigs() {
        // Создание папки плагина, если её нет
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.config = loadSpecificConfig(this.configFile, "config.yml");
        this.pluginLocale = loadSpecificConfig(this.pluginLocaleFile, "plugin_locale.yml");
        this.discordLocale = loadSpecificConfig(this.discordLocaleFile, "discord_locale.yml");
    }

    private FileConfiguration loadSpecificConfig(final File file, final String resourceName) {
        if (!file.exists()) {
            // Сохраняем файл из ресурсов Jar, если его нет на диске
            plugin.saveResource(resourceName, false);
        }

        final YamlConfiguration yamlConfig = YamlConfiguration.loadConfiguration(file);

        // Слияние с дефолтным конфигом из ресурсов для добавления новых полей (если обновилась версия плагина)
        final InputStream defaultStream = plugin.getResource(resourceName);
        if (defaultStream != null) {
            try (final InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
                final YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
                yamlConfig.setDefaults(defaultConfig);
                yamlConfig.options().copyDefaults(true);
                yamlConfig.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось обновить конфигурационный файл: " + resourceName, e);
            }
        }
        return yamlConfig;
    }

    public FileConfiguration getConfig() {
        return this.config;
    }

    public FileConfiguration getPluginLocale() {
        return this.pluginLocale;
    }

    public FileConfiguration getDiscordLocale() {
        return this.discordLocale;
    }
}