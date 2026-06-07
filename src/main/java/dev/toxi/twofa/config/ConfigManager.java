/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.config;

import dev.toxi.twofa.TwoFactorPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Level;

public final class ConfigManager {

    private static final String DEFAULT_LOCALE = "en_us";

    private final TwoFactorPlugin plugin;

    private FileConfiguration config;
    private FileConfiguration locale;
    private String activeLocale = DEFAULT_LOCALE;

    private final File configFile;
    private final File localeDirectory;

    public ConfigManager(final TwoFactorPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.localeDirectory = new File(plugin.getDataFolder(), "locale");
    }

    // Полная перезагрузка или первичная инициализация всех конфигов
    public void reloadConfigs() {
        // Создание папки плагина, если её нет
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.config = loadSpecificConfig(this.configFile, "config.yml");
        this.locale = loadLocaleConfig();
    }

    private FileConfiguration loadLocaleConfig() {
        if (!localeDirectory.exists()) {
            localeDirectory.mkdirs();
        }

        final String requestedLocale = config.getString("locale", DEFAULT_LOCALE).toLowerCase(Locale.ROOT);
        final String resourceName = "locale/" + requestedLocale + ".yml";

        if (plugin.getResource(resourceName) != null) {
            this.activeLocale = requestedLocale;
            return loadSpecificConfig(new File(localeDirectory, requestedLocale + ".yml"), resourceName);
        }

        plugin.getLogger().warning("Неизвестная локаль '" + requestedLocale + "'. Используется локаль по умолчанию: " + DEFAULT_LOCALE);
        this.activeLocale = DEFAULT_LOCALE;
        return loadSpecificConfig(new File(localeDirectory, DEFAULT_LOCALE + ".yml"), "locale/" + DEFAULT_LOCALE + ".yml");
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

    public FileConfiguration getLocale() {
        return this.locale;
    }

    public String getActiveLocale() {
        return this.activeLocale;
    }
}
