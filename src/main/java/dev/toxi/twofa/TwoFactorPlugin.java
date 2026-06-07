/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa;

import dev.toxi.twofa.bot.DiscordBotManager;
import dev.toxi.twofa.command.TwoFactorCommand;
import dev.toxi.twofa.config.ConfigManager;
import dev.toxi.twofa.database.DatabaseManager;
import dev.toxi.twofa.listener.PlayerFreezeListener;
import dev.toxi.twofa.listener.PlayerJoinListener;
import dev.toxi.twofa.listener.PlayerQuitListener;
import dev.toxi.twofa.service.AuthService;
import dev.toxi.twofa.service.CodeGeneratorService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class TwoFactorPlugin extends JavaPlugin {

    private static TwoFactorPlugin instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private AuthService authService;
    private CodeGeneratorService codeGeneratorService;
    private DiscordBotManager discordBotManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Инициализация менеджера конфигураций
        this.configManager = new ConfigManager(this);
        this.configManager.reloadConfigs();

        // 2. Инициализация базы данных (HikariCP)
        this.databaseManager = new DatabaseManager(this);
        if (!this.databaseManager.initialize()) {
            getLogger().log(Level.SEVERE, "Не удалось подключиться к базе данных! Плагин выключается...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Инициализация бизнес-сервисов
        this.authService = new AuthService(this);
        this.codeGeneratorService = new CodeGeneratorService(this);

        // 4. Запуск Discord-бота (асинхронно внутри менеджера)
        this.discordBotManager = new DiscordBotManager(this, this.authService, this.codeGeneratorService);
        this.discordBotManager.start();

        // 5. Регистрация событий Minecraft
        registerListeners();

        // 6. Регистрация команд
        registerCommands();

        getLogger().info("Плагин 2FA успешно запущен и готов к работе.");
    }

    @Override
    public void onDisable() {
        // Безопасная остановка Discord бота
        if (this.discordBotManager != null) {
            this.discordBotManager.stop();
        }

        // Безопасное закрытие пула соединений БД
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }

        getLogger().info("Плагин 2FA успешно выключен.");
    }

    /**
     * Выполняет горячую перезагрузку всех конфигураций и полное
     * переподключение к сервисам БД и Discord без перезапуска сервера.
     */
    public void reloadPlugin() {
        getLogger().info("Начата горячая перезагрузка конфигураций и сервисов...");

        // 1. Перезагружаем файлы конфигурации с диска
        this.configManager.reloadConfigs();

        // 2. Пересоздаем и безопасно перезапускаем пул базы данных
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        this.databaseManager = new DatabaseManager(this);
        if (!this.databaseManager.initialize()) {
            getLogger().log(Level.SEVERE, "Критическая ошибка! Не удалось переинициализировать БД при перезагрузке!");
        }

        // 3. Безопасно выключаем старого бота и запускаем новую сессию JDA
        if (this.discordBotManager != null) {
            this.discordBotManager.stop();
        }
        this.discordBotManager = new DiscordBotManager(this, this.authService, this.codeGeneratorService);
        this.discordBotManager.start();

        getLogger().info("Плагин успешно перезагружен и применил новые настройки!");
    }

    private void registerListeners() {
        final var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this, this.authService), this);
        pm.registerEvents(new PlayerFreezeListener(this, this.authService), this);
        pm.registerEvents(new PlayerQuitListener(this.authService), this);
    }

    private void registerCommands() {
        final TwoFactorCommand mainCommand = new TwoFactorCommand(this);
        final var pluginCommand = getCommand("2fa");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(mainCommand);
            pluginCommand.setTabCompleter(mainCommand);
        }
    }

    public static TwoFactorPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return this.databaseManager;
    }

    public AuthService getAuthService() {
        return this.authService;
    }

    public CodeGeneratorService getCodeGeneratorService() {
        return this.codeGeneratorService;
    }

    public DiscordBotManager getDiscordBotManager() {
        return this.discordBotManager;
    }
}
