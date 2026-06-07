/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.toxi.twofa.TwoFactorPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.logging.Level;

public final class DatabaseManager {

    private static final String USERS_TABLE = "twofa_users";
    private static final String BANS_TABLE = "twofa_bans";
    private static final String LEGACY_USERS_TABLE = "aurion_2fa_users";
    private static final String LEGACY_BANS_TABLE = "aurion_2fa_bans";

    private final TwoFactorPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(final TwoFactorPlugin plugin) {
        this.plugin = plugin;
    }

    // Инициализация пула соединений
    public boolean initialize() {
        final var config = plugin.getConfigManager().getConfig();
        final String type = getDatabaseType();

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("2FA-Pool");

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
            final String database = config.getString("database.database", "twofa");
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
                CREATE TABLE IF NOT EXISTS twofa_users (
                    uuid VARCHAR(36) PRIMARY KEY,
                    discord_id VARCHAR(20) NOT NULL UNIQUE,
                    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );""";

        final String bansTable = """
                CREATE TABLE IF NOT EXISTS twofa_bans (
                    uuid VARCHAR(36) PRIMARY KEY,
                    banned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );""";

        try (final Connection conn = getConnection();
             final Statement stmt = conn.createStatement()) {

            stmt.execute(usersTable);
            stmt.execute(bansTable);
            migrateLegacyTables(conn);

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

    public String getUsersTableName() {
        return USERS_TABLE;
    }

    public String getBansTableName() {
        return BANS_TABLE;
    }

    private String getDatabaseType() {
        return plugin.getConfigManager().getConfig().getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
    }

    private void migrateLegacyTables(final Connection connection) {
        try (final Statement stmt = connection.createStatement()) {
            if (tableExists(connection, LEGACY_USERS_TABLE) && isTableEmpty(connection, USERS_TABLE)) {
                stmt.executeUpdate("INSERT INTO " + USERS_TABLE + " (uuid, discord_id, linked_at) SELECT uuid, discord_id, linked_at FROM " + LEGACY_USERS_TABLE);
                plugin.getLogger().info("Найдены старые данные 2FA: связи аккаунтов перенесены в новую схему.");
            }

            if (tableExists(connection, LEGACY_BANS_TABLE) && isTableEmpty(connection, BANS_TABLE)) {
                stmt.executeUpdate("INSERT INTO " + BANS_TABLE + " (uuid, banned_at) SELECT uuid, banned_at FROM " + LEGACY_BANS_TABLE);
                plugin.getLogger().info("Найдены старые данные 2FA: блокировки перенесены в новую схему.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось выполнить миграцию старых таблиц 2FA.", e);
        }
    }

    private boolean tableExists(final Connection connection, final String tableName) throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        if (hasTable(metaData, tableName)) {
            return true;
        }
        return hasTable(metaData, tableName.toUpperCase(Locale.ROOT));
    }

    private boolean hasTable(final DatabaseMetaData metaData, final String tableName) throws SQLException {
        try (final ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean isTableEmpty(final Connection connection, final String tableName) throws SQLException {
        try (final Statement stmt = connection.createStatement();
             final ResultSet rs = stmt.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1")) {
            return !rs.next();
        }
    }
}
