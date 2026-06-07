/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.listener;

import dev.toxi.twofa.TwoFactorPlugin;
import dev.toxi.twofa.service.AuthService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerQuitListener implements Listener {

    private final AuthService authService;

    public PlayerQuitListener(final AuthService authService) {
        this.authService = authService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(final PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        // 1. Очищаем все временные состояния
        authService.unfreeze(uuid);
        authService.clearCachedLinkStatus(uuid);

        // 2. Реализуем скрытый кик (stealth kick)
        if (authService.consumeStealthKick(uuid)) {
            // Удаляем глобальное сообщение в чате Minecraft о выходе игрока
            event.quitMessage(null);
        }
    }
}