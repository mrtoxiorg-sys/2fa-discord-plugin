/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.database;

import dev.toxi.twofa.TwoFactorPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class UserDao {

    private final TwoFactorPlugin plugin;

    public UserDao(final TwoFactorPlugin plugin) {
        this.plugin = plugin;
    }

    // Получение Discord ID по UUID игрока
    public Optional<String> getDiscordId(final UUID uuid) {
        final String sql = "SELECT discord_id FROM " + usersTable() + " WHERE uuid = ?;";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("discord_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при получении discord_id по UUID: " + uuid, e);
        }
        return Optional.empty();
    }

    // Получение UUID игрока по Discord ID
    public Optional<UUID> getMinecraftUuid(final String discordId) {
        final String sql = "SELECT uuid FROM " + usersTable() + " WHERE discord_id = ?;";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);
            try (final ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UUID.fromString(rs.getString("uuid")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при получении UUID по discord_id: " + discordId, e);
        }
        return Optional.empty();
    }

    // Создание связки игрока и Discord-аккаунта
    public boolean linkUser(final UUID uuid, final String discordId) {
        final String sql = switch (databaseType()) {
            case "mysql", "mariadb" -> "INSERT INTO " + usersTable() + " (uuid, discord_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE discord_id = VALUES(discord_id), linked_at = CURRENT_TIMESTAMP;";
            default -> "INSERT INTO " + usersTable() + " (uuid, discord_id) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET discord_id = excluded.discord_id, linked_at = CURRENT_TIMESTAMP;";
        };
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, discordId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при записи связки в БД для UUID: " + uuid, e);
            return false;
        }
    }

    // Удаление связки по UUID
    public boolean unlinkUser(final UUID uuid) {
        final String sql = "DELETE FROM " + usersTable() + " WHERE uuid = ?;";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при удалении связки для UUID: " + uuid, e);
            return false;
        }
    }

    // Удаление связки по Discord ID
    public boolean unlinkUserByDiscordId(final String discordId) {
        final String sql = "DELETE FROM " + usersTable() + " WHERE discord_id = ?;";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при удалении связки для Discord ID: " + discordId, e);
            return false;
        }
    }

    // Проверка наличия игрока в бане плагина
    public boolean isBanned(final UUID uuid) {
        final String sql = "SELECT 1 FROM " + bansTable() + " WHERE uuid = ?;";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            try (final ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при проверке бана для UUID: " + uuid, e);
        }
        return false;
    }

    // Блокировка игрока на стороне плагина
    public boolean banUser(final UUID uuid) {
        final String sql = switch (databaseType()) {
            case "mysql", "mariadb" -> "INSERT IGNORE INTO " + bansTable() + " (uuid) VALUES (?);";
            default -> "INSERT INTO " + bansTable() + " (uuid) VALUES (?) ON CONFLICT(uuid) DO NOTHING;";
        };
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при блокировке UUID: " + uuid, e);
            return false;
        }
    }

    // Разблокировка игрока по его UUID
    public boolean unbanUser(final UUID uuid) {
        final String sql = "DELETE FROM " + bansTable() + " WHERE uuid = ?;";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при разблокировке UUID: " + uuid, e);
            return false;
        }
    }

    // Разблокировка игрока по Discord ID (находит UUID по связи и удаляет бан)
    public boolean unbanUserByDiscordId(final String discordId) {
        final String sql = "DELETE FROM " + bansTable() + " WHERE uuid = (SELECT uuid FROM " + usersTable() + " WHERE discord_id = ?);";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при разблокировке через Discord ID: " + discordId, e);
            return false;
        }
    }

    private String usersTable() {
        return plugin.getDatabaseManager().getUsersTableName();
    }

    private String bansTable() {
        return plugin.getDatabaseManager().getBansTableName();
    }

    private String databaseType() {
        return plugin.getConfigManager().getConfig().getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
    }
}
