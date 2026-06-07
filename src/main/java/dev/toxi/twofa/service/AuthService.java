/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.service;

import dev.toxi.twofa.TwoFactorPlugin;
import dev.toxi.twofa.database.UserDao;
import dev.toxi.twofa.model.AuthSession;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private final TwoFactorPlugin plugin;
    private final UserDao userDao;

    // Сессии авторизации (UUID -> Сессия), TTL настраивается в config.yml
    private final Map<UUID, AuthSession> activeSessions = new ConcurrentHashMap<>();

    // Множество замороженных игроков, ожидающих подтверждения в Discord
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    // Множество UUID игроков, которых нужно кикнуть без глобального сообщения в чате
    private final Set<UUID> stealthKickedPlayers = ConcurrentHashMap.newKeySet();

    // Кэш статуса привязки во время входа (UUID -> Имеет ли привязку к ДС)
    private final Map<UUID, Boolean> linkStatusCache = new ConcurrentHashMap<>();

    public AuthService(final TwoFactorPlugin plugin) {
        this.plugin = plugin;
        this.userDao = new UserDao(plugin);

        // Шедулер для очистки устаревших сессий раз в минуту
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanExpiredSessions, 1200L, 1200L);
    }

    // Проверка, заморожен ли игрок прямо сейчас
    public boolean isFrozen(final UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    public void freeze(final UUID uuid) {
        frozenPlayers.add(uuid);
    }

    public void unfreeze(final UUID uuid) {
        frozenPlayers.remove(uuid);
    }

    // Создание новой сессии IP-адреса
    public void createSession(final UUID uuid, final String ipAddress) {
        activeSessions.put(uuid, new AuthSession(uuid, ipAddress, Instant.now()));
    }

    // Проверка, есть ли у игрока действующая сессия для текущего IP
    public boolean hasValidSession(final UUID uuid, final String currentIp) {
        final AuthSession session = activeSessions.get(uuid);
        if (session == null) {
            return false;
        }

        final long maxDurationSeconds = plugin.getConfigManager().getConfig().getLong("session-duration-seconds", 3600L);
        if (session.isExpired(maxDurationSeconds)) {
            activeSessions.remove(uuid);
            return false;
        }

        // Сессия валидна, если IP совпадает
        return session.ipAddress().equals(currentIp);
    }

    // Добавление игрока в список для тихого кика
    public void markStealthKick(final UUID uuid) {
        stealthKickedPlayers.add(uuid);
    }

    // Проверка и удаление отметки тихого кика
    public boolean consumeStealthKick(final UUID uuid) {
        return stealthKickedPlayers.remove(uuid);
    }

    // Кэширование статуса привязки на этапе пре-логина
    public void cacheLinkStatus(final UUID uuid, final boolean isLinked) {
        linkStatusCache.put(uuid, isLinked);
    }

    public Boolean getCachedLinkStatus(final UUID uuid) {
        return linkStatusCache.get(uuid);
    }

    public void clearCachedLinkStatus(final UUID uuid) {
        linkStatusCache.remove(uuid);
    }

    private void cleanExpiredSessions() {
        final long maxDurationSeconds = plugin.getConfigManager().getConfig().getLong("session-duration-seconds", 3600L);
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired(maxDurationSeconds));
    }

    public UserDao getUserDao() {
        return this.userDao;
    }

    /*
     * Асинхронные обертки над DAO-операциями для безопасного вызова из любых потоков
     */

    public CompletableFuture<Boolean> isBannedAsync(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> userDao.isBanned(uuid));
    }

    public CompletableFuture<Boolean> banUserAsync(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> userDao.banUser(uuid));
    }

    public CompletableFuture<Boolean> unbanUserAsync(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> userDao.unbanUser(uuid));
    }

    public CompletableFuture<Boolean> unbanUserByDiscordIdAsync(final String discordId) {
        return CompletableFuture.supplyAsync(() -> userDao.unbanUserByDiscordId(discordId));
    }

    public CompletableFuture<Boolean> unlinkUserAsync(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> userDao.unlinkUser(uuid));
    }

    public CompletableFuture<Boolean> unlinkUserByDiscordIdAsync(final String discordId) {
        return CompletableFuture.supplyAsync(() -> userDao.unlinkUserByDiscordId(discordId));
    }

    public CompletableFuture<Boolean> linkUserAsync(final UUID uuid, final String discordId) {
        return CompletableFuture.supplyAsync(() -> userDao.linkUser(uuid, discordId));
    }

    public CompletableFuture<Optional<String>> getDiscordIdAsync(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> userDao.getDiscordId(uuid));
    }
}