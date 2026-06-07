/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Неизменяемая модель сессии авторизованного игрока.
 *
 * @param uuid      UUID игрока в Minecraft
 * @param ipAddress IP-адрес, с которого был совершён вход
 * @param createdAt Время создания/последнего подтверждения сессии
 */
public record AuthSession(UUID uuid, String ipAddress, Instant createdAt) {

    // Проверяет, истекло ли время жизни сессии на основе переданного лимита (в секундах)
    public boolean isExpired(final long maxDurationSeconds) {
        return Instant.now().isAfter(createdAt.plusSeconds(maxDurationSeconds));
    }
}