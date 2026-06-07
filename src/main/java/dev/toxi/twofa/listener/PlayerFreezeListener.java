/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.listener;

import dev.toxi.twofa.TwoFactorPlugin;
import dev.toxi.twofa.service.AuthService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

public final class PlayerFreezeListener implements Listener {

    private final TwoFactorPlugin plugin;
    private final AuthService authService;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerFreezeListener(final TwoFactorPlugin plugin, final AuthService authService) {
        this.plugin = plugin;
        this.authService = authService;
    }

    // Заморозка перемещения (Позволяет крутить головой, но блокирует ходьбу)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (authService.isFrozen(player.getUniqueId())) {
            final Location from = event.getFrom();
            final Location to = event.getTo();

            // Если изменились координаты X, Y или Z (игнорируем поворот головы Pitch/Yaw)
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                // Возвращаем на исходную позицию, сохраняя направление взгляда
                event.setTo(from.setDirection(to.getDirection()));
            }
        }
    }

    // Блокировка отправки сообщений в чат
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(final AsyncChatEvent event) {
        if (authService.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendWarnMessage(event.getPlayer());
        }
    }

    // Блокировка выполнения команд
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(final PlayerCommandPreprocessEvent event) {
        if (authService.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendWarnMessage(event.getPlayer());
        }
    }

    // Блокировка любых взаимодействий (блоки, сундуки, инвентарь)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(final PlayerInteractEvent event) {
        if (authService.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Блокировка кликов в инвентаре
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (authService.isFrozen(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Блокировка выбрасывания предметов
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(final PlayerDropItemEvent event) {
        if (authService.isFrozen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Блокировка поднятия предметов
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickupItem(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && authService.isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Блокировка получения любого урона (включая голод, падение и т.д.)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && authService.isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Блокировка нанесения урона другим сущностям
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageEntity(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && authService.isFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void sendWarnMessage(final Player player) {
        final String prefix = plugin.getConfigManager().getLocale().getString("prefix", "");
        final String rawMsg = plugin.getConfigManager().getLocale().getString("messages.actions-blocked", "%prefix%<red>Error:</red> <gray>You cannot do that until 2FA is completed.</gray>");
        player.sendMessage(miniMessage.deserialize(rawMsg.replace("%prefix%", prefix)));
    }
}
