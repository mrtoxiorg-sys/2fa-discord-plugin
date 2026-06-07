/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.service;

import dev.toxi.aurion2fa.Aurion2fa;
import dev.toxi.aurion2fa.model.LinkCode;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CodeGeneratorService {

    private final Aurion2fa plugin;
    // Безопасный генератор случайных чисел
    private final SecureRandom random = new SecureRandom();
    // Хранилище кодов в оперативной памяти (Потокобезопасное)
    private final Map<String, LinkCode> activeCodes = new ConcurrentHashMap<>();

    public CodeGeneratorService(final Aurion2fa plugin) {
        this.plugin = plugin;

        // Запуск асинхронного шедулера для очистки кэша раз в 30 секунд
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanExpiredCodes, 600L, 600L);
    }

    /**
     * Генерирует уникальный 4-значный код для игрока с временем жизни 1 минута.
     * Если у игрока уже был активный код, он перезаписывается.
     */
    public synchronized String generateCode(final UUID playerUuid) {
        // Удаляем старый код этого игрока из карты, если он существовал
        activeCodes.values().removeIf(linkCode -> linkCode.playerUuid().equals(playerUuid));

        String code;
        // Генерируем уникальное значение, исключая маловероятное совпадение с чужим активным кодом
        do {
            code = String.format("%04d", random.nextInt(10000));
        } while (activeCodes.containsKey(code));

        final Instant expiresAt = Instant.now().plus(Duration.ofMinutes(1));
        final LinkCode linkCode = new LinkCode(playerUuid, code, expiresAt);
        activeCodes.put(code, linkCode);

        if (plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] Создан код привязки для " + playerUuid + ": " + code + " (TTL: 1m)");
        }

        return code;
    }

    /**
     * Поиск UUID игрока по переданному коду.
     * Если код действителен и его время жизни не истекло, возвращается UUID.
     */
    public Optional<UUID> consumeCode(final String code) {
        final LinkCode linkCode = activeCodes.get(code);
        if (linkCode == null) {
            return Optional.empty();
        }

        if (linkCode.isExpired()) {
            activeCodes.remove(code);
            return Optional.empty();
        }

        // Код использован - удаляем его сразу
        activeCodes.remove(code);
        return Optional.of(linkCode.playerUuid());
    }

    // Асинхронная очистка просроченных кодов из памяти
    private void cleanExpiredCodes() {
        final int beforeSize = activeCodes.size();
        activeCodes.values().removeIf(LinkCode::isExpired);

        final int cleared = beforeSize - activeCodes.size();
        if (cleared > 0 && plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] Очищено просроченных кодов привязки: " + cleared);
        }
    }
}