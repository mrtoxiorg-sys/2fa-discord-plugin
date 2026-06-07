/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Неизменяемая модель (Record) временного кода для привязки аккаунта Discord.
 *
 * @param playerUuid UUID игрока в Minecraft
 * @param code       4-значный уникальный числовой код
 * @param expiresAt  Время, после которого код считается недействительным
 */
public record LinkCode(UUID playerUuid, String code, Instant expiresAt) {

    // Проверяет, истёк ли срок действия кода
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}