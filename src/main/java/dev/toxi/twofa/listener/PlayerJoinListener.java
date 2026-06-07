/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.listener;

import dev.toxi.twofa.TwoFactorPlugin;
import dev.toxi.twofa.bot.DiscordEmbedService;
import dev.toxi.twofa.service.AuthService;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public final class PlayerJoinListener implements Listener {

    private final TwoFactorPlugin plugin;
    private final AuthService authService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerJoinListener(final TwoFactorPlugin plugin, final AuthService authService) {
        this.plugin = plugin;
        this.authService = authService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(final AsyncPlayerPreLoginEvent event) {
        final UUID uuid = event.getUniqueId();

        // Сначала проверяем системные баны 2FA
        if (authService.getUserDao().isBanned(uuid)) {
            final String kickRaw = plugin.getConfigManager().getPluginLocale().getString("messages.banned-kick", "<red>Ваш аккаунт заблокирован через Discord 2FA.");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, miniMessage.deserialize(kickRaw));
            return;
        }

        final boolean isLinked = authService.getUserDao().getDiscordId(uuid).isPresent();
        authService.cacheLinkStatus(uuid, isLinked);

        // КРИТИЧЕСКИЙ ФИКС: Проверка готовности бота перенесена на этап PreLogin.
        // Если бот не запущен, а игроку требуется авторизация — отклоняем вход до создания сущности игрока.
        // Это предотвращает появление ошибки ChunkMap::addEntity и зависание загрузки мира.
        if (isLinked) {
            final var botManager = plugin.getDiscordBotManager();
            if (botManager == null || !botManager.isReady()) {
                authService.clearCachedLinkStatus(uuid);
                final String notReadyMsg = plugin.getConfigManager().getPluginLocale().getString(
                        "messages.bot-not-ready",
                        "<red>Вход отклонен: Система защиты 2FA еще запускается после старта сервера. Пожалуйста, подождите около минуты и попробуйте зайти снова."
                );
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, miniMessage.deserialize(notReadyMsg));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        final Boolean isLinked = authService.getCachedLinkStatus(uuid);
        if (isLinked == null || !isLinked) {
            authService.clearCachedLinkStatus(uuid);
            return;
        }

        final String ipAddress = player.getAddress().getAddress().getHostAddress();

        if (authService.hasValidSession(uuid, ipAddress)) {
            authService.clearCachedLinkStatus(uuid);
            authService.createSession(uuid, ipAddress);
            return;
        }

        // Замораживаем игрока перед отправкой 2FA
        authService.freeze(uuid);

        // Информируем в чат Minecraft
        final String prefix = plugin.getConfigManager().getPluginLocale().getString("prefix", "");
        final String rawMsg = plugin.getConfigManager().getPluginLocale().getString("messages.auth-required", "%prefix%<gradient:#FF9F4F:#FAD7A7>Внимание →</gradient> <white>Подтвердите вход в Discord!");
        player.sendMessage(miniMessage.deserialize(rawMsg.replace("%prefix%", prefix)));

        // Асинхронно достаем Discord ID и отправляем запрос в ЛС
        authService.getDiscordIdAsync(uuid).thenAccept(optDiscord -> {
            if (optDiscord.isEmpty()) {
                // Если связи почему-то нет, размораживаем игрока на основном потоке
                plugin.getServer().getScheduler().runTask(plugin, () -> authService.unfreeze(uuid));
                return;
            }

            final String discordId = optDiscord.get();
            final MessageCreateData authRequest = DiscordEmbedService.createAuthRequest(plugin, uuid, player.getName(), ipAddress);

            // Отправка ЛС в Discord
            plugin.getDiscordBotManager().sendPrivateMessage(discordId, authRequest).exceptionally(error -> {
                // ФИКС: Для предотвращения поломки ChunkMap при выходе выполняем kick с задержкой в 1 тик
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    final String rawKickMsg = plugin.getConfigManager().getPluginLocale().getString("messages.dm-failed", "<red>Вход отклонен: Бот не смог отправить вам запрос в ЛС Discord. Пожалуйста, откройте личные сообщения и зайдите снова.");
                    player.kick(miniMessage.deserialize(rawKickMsg));

                    if (plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().warning("[DEBUG] Не удалось отправить ЛС игроку " + player.getName() + ", он был кикнут на следующем тике.");
                    }
                }, 1L);
                return null;
            });
        });

        authService.clearCachedLinkStatus(uuid);
    }
}