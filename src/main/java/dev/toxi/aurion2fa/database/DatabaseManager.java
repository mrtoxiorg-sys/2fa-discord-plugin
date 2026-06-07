/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.toxi.aurion2fa.Aurion2fa;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public final class DatabaseManager {

    private final Aurion2fa plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(final Aurion2fa plugin) {
        this.plugin = plugin;
    }

    // Инициализация пула соединений
    public boolean initialize() {
        final var config = plugin.getConfigManager().getConfig();
        final String type = config.getString("database.type", "sqlite").toLowerCase();

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("Aurion2FA-Pool");

        // Настройки тайм-аутов пула
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(5000); // 5 секунд на ожидание соединения
        hikariConfig.setIdleTimeout(600000);     // 10 минут
        hikariConfig.setMaxLifetime(1800000);    // 30 минут

        if (type.equals("sqlite")) {
            final File dbFile = new File(plugin.getDataFolder(), "database.db");
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } else {
            // Поддержка внешних СУБД (MySQL / PostgreSQL / MariaDB)
            final String host = config.getString("database.host", "localhost");
            final int port = config.getInt("database.port", 3306);
            final String database = config.getString("database.database", "aurion2fa");
            final String username = config.getString("database.username", "root");
            final String password = config.getString("database.password", "");
            final boolean useSSL = config.getBoolean("database.use-ssl", false);

            final String driverClass = type.equals("postgresql") ? "org.postgresql.Driver" : "com.mysql.cj.jdbc.Driver";
            final String jdbcUrl = String.format("jdbc:%s://%s:%d/%s?useSSL=%b", type, host, port, database, useSSL);

            hikariConfig.setDriverClassName(driverClass);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
        }

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при подключении к базе данных через HikariCP!", e);
            return false;
        }
    }

    // Получение потокобезопасного соединения из пула
    public Connection getConnection() throws SQLException {
        if (this.dataSource == null) {
            throw new SQLException("База данных не инициализирована.");
        }
        return this.dataSource.getConnection();
    }

    // Создание таблиц при запуске
    private void createTables() {
        final String usersTable = """
                CREATE TABLE IF NOT EXISTS aurion_2fa_users (
                    uuid VARCHAR(36) PRIMARY KEY,
                    discord_id VARCHAR(20) NOT NULL UNIQUE,
                    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );""";

        final String bansTable = """
                CREATE TABLE IF NOT EXISTS aurion_2fa_bans (
                    uuid VARCHAR(36) PRIMARY KEY,
                    banned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );""";

        try (final Connection conn = getConnection();
             final Statement stmt = conn.createStatement()) {

            stmt.execute(usersTable);
            stmt.execute(bansTable);

            if (plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Базы данных проверены/созданы успешно.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось создать таблицы в базе данных!", e);
        }
    }

    // Закрытие пула при отключении плагина
    public void close() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }
}