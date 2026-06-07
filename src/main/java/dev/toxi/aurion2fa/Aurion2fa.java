/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa;

import dev.toxi.aurion2fa.bot.DiscordBotManager;
import dev.toxi.aurion2fa.command.Aurion2faCommand;
import dev.toxi.aurion2fa.config.ConfigManager;
import dev.toxi.aurion2fa.database.DatabaseManager;
import dev.toxi.aurion2fa.listener.PlayerFreezeListener;
import dev.toxi.aurion2fa.listener.PlayerJoinListener;
import dev.toxi.aurion2fa.listener.PlayerQuitListener;
import dev.toxi.aurion2fa.service.AuthService;
import dev.toxi.aurion2fa.service.CodeGeneratorService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class Aurion2fa extends JavaPlugin {

    private static Aurion2fa instance;

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

        getLogger().info("Плагин Aurion2FA успешно запущен и интегрирован!");
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

        getLogger().info("Плагин Aurion2FA успешно выключен.");
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
        final Aurion2faCommand mainCommand = new Aurion2faCommand(this);
        final var pluginCommand = getCommand("aurion2fa");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(mainCommand);
            pluginCommand.setTabCompleter(mainCommand);
        }
    }

    public static Aurion2fa getInstance() {
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