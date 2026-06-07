/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.database;

import dev.toxi.aurion2fa.Aurion2fa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class UserDao {

    private final Aurion2fa plugin;

    public UserDao(final Aurion2fa plugin) {
        this.plugin = plugin;
    }

    // Получение Discord ID по UUID игрока
    public Optional<String> getDiscordId(final UUID uuid) {
        final String sql = "SELECT discord_id FROM aurion_2fa_users WHERE uuid = ?;";
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
        final String sql = "SELECT uuid FROM aurion_2fa_users WHERE discord_id = ?;";
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
        final String sql = "INSERT INTO aurion_2fa_users (uuid, discord_id) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET discord_id = EXCLUDED.discord_id;";
        // Примечание: ON CONFLICT синтаксис поддерживается в SQLite и PostgreSQL. Для MySQL в продакшене мы адаптируем запрос, если потребуется.
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
        final String sql = "DELETE FROM aurion_2fa_users WHERE uuid = ?;";
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
        final String sql = "DELETE FROM aurion_2fa_users WHERE discord_id = ?;";
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
        final String sql = "SELECT 1 FROM aurion_2fa_bans WHERE uuid = ?;";
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
        final String sql = "INSERT OR IGNORE INTO aurion_2fa_bans (uuid) VALUES (?);";
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
        final String sql = "DELETE FROM aurion_2fa_bans WHERE uuid = ?;";
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
        final String sql = "DELETE FROM aurion_2fa_bans WHERE uuid = (SELECT uuid FROM aurion_2fa_users WHERE discord_id = ?);";
        try (final Connection conn = plugin.getDatabaseManager().getConnection();
             final PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при разблокировке через Discord ID: " + discordId, e);
            return false;
        }
    }
}