/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.bot;

import dev.toxi.aurion2fa.Aurion2fa;
import dev.toxi.aurion2fa.service.AuthService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DiscordButtonListener extends ListenerAdapter {

    private final Aurion2fa plugin;
    private final AuthService authService;

    public DiscordButtonListener(final Aurion2fa plugin, final AuthService authService) {
        this.plugin = plugin;
        this.authService = authService;
    }

    @Override
    public void onButtonInteraction(final @NotNull ButtonInteractionEvent event) {
        final String customId = event.getButton().getId();
        if (customId == null) return;

        final String[] parts = customId.split(":");
        if (parts.length != 2) return;

        final String action = parts[0];
        final UUID uuid;
        try {
            uuid = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Подтверждаем нажатие кнопки асинхронно.
        // Передаем кастомный consumer ошибок, чтобы предотвратить появление логов "Unknown interaction",
        // если кнопка была нажата во время сетевого сбоя или старым процессом бота.
        event.deferEdit().queue(
                success -> {},
                throwable -> {
                    if (plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().warning("[DEBUG] Ошибка подтверждения клика в Discord: " + throwable.getMessage());
                    }
                }
        );

        switch (action) {
            case "approve" -> handleApprove(event, uuid);
            case "kick" -> handleKick(event, uuid);
            case "block" -> handleBlock(event, uuid);
        }
    }

    private void handleApprove(final ButtonInteractionEvent event, final UUID uuid) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {

                // Проверяем, заморожен ли ещё игрок на сервере.
                if (!authService.isFrozen(uuid)) {
                    updateDiscordMessage(event, "Вход уже подтвержден", "Вход для этого игрока уже был одобрен ранее.");
                    return;
                }

                final String ip = player.getAddress().getAddress().getHostAddress();

                authService.unfreeze(uuid);
                authService.createSession(uuid, ip);

                final String prefix = plugin.getConfigManager().getPluginLocale().getString("prefix", "");
                final String rawMsg = plugin.getConfigManager().getPluginLocale().getString("messages.auth-success", "%prefix%<gradient:#4FD6FF:#D7F4FA>Успешно →</gradient> <white>Вход успешно подтвержден!");
                player.sendMessage(MiniMessage.miniMessage().deserialize(rawMsg.replace("%prefix%", prefix)));

                updateDiscordMessage(event, "Вход подтвержден", "Вход для этого игрока был успешно **одобрен**.");
            } else {
                updateDiscordMessage(event, "Игрок не в сети", "Срок действия запроса истёк, так как игрок уже вышел из игры.");
            }
        });
    }

    private void handleKick(final ButtonInteractionEvent event, final UUID uuid) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                authService.markStealthKick(uuid);

                final String kickMsg = plugin.getConfigManager().getPluginLocale().getString("messages.auth-kick-reason", "<red>Вход был отклонен вами через Discord.");
                player.kick(MiniMessage.miniMessage().deserialize(kickMsg));

                updateDiscordMessage(event, "Игрок кикнут", "Игрок был успешно **кикнут** с сервера.");
            } else {
                updateDiscordMessage(event, "Игрок не в сети", "Не удалось кикнуть игрока, так как он уже не в сети.");
            }
        });
    }

    private void handleBlock(final ButtonInteractionEvent event, final UUID uuid) {
        authService.banUserAsync(uuid).thenRun(() -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                final Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    authService.markStealthKick(uuid);
                    final String kickMsg = plugin.getConfigManager().getPluginLocale().getString("messages.auth-kick-blocked", "<red>Ваш аккаунт заблокирован через Discord.");
                    player.kick(MiniMessage.miniMessage().deserialize(kickMsg));
                }

                updateDiscordMessage(event, "Аккаунт заблокирован", "Аккаунт был занесен в **черный список** плагина, вход заблокирован.");
            });
        });
    }

    private void updateDiscordMessage(final ButtonInteractionEvent event, final String statusTitle, final String statusValue) {
        final Message message = event.getMessage();
        if (message.getEmbeds().isEmpty()) return;

        final MessageEmbed oldEmbed = message.getEmbeds().get(0);
        final EmbedBuilder newEmbed = new EmbedBuilder(oldEmbed)
                .addField("Статус действия", statusValue, false)
                .setColor(statusTitle.contains("заблокирован") ? java.awt.Color.RED : java.awt.Color.GREEN);

        final List<ActionRow> disabledRows = new ArrayList<>();
        for (final ActionRow row : message.getActionRows()) {
            final List<Button> disabledButtons = row.getButtons().stream()
                    .map(Button::asDisabled)
                    .toList();
            disabledRows.add(ActionRow.of(disabledButtons));
        }

        // Обновляем исходное сообщение с обработкой ошибок
        event.getHook().editOriginal(
                new MessageEditBuilder()
                        .setEmbeds(newEmbed.build())
                        .setComponents(disabledRows)
                        .build()
        ).queue(
                success -> {},
                throwable -> {
                    if (plugin.getConfigManager().getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().warning("[DEBUG] Не удалось обновить интерактивное сообщение в Discord: " + throwable.getMessage());
                    }
                }
        );
    }
}