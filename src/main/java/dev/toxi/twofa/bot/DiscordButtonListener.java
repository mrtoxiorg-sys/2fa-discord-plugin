/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.bot;

import dev.toxi.twofa.TwoFactorPlugin;
import dev.toxi.twofa.service.AuthService;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DiscordButtonListener extends ListenerAdapter {

    private final TwoFactorPlugin plugin;
    private final AuthService authService;

    public DiscordButtonListener(final TwoFactorPlugin plugin, final AuthService authService) {
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
                    updateDiscordMessage(event, "already-approved");
                    return;
                }

                final String ip = player.getAddress().getAddress().getHostAddress();

                authService.unfreeze(uuid);
                authService.createSession(uuid, ip);

                final String prefix = plugin.getConfigManager().getLocale().getString("prefix", "");
                final String rawMsg = plugin.getConfigManager().getLocale().getString("messages.auth-success", "%prefix%<green>Success:</green> <gray>Login confirmed.</gray>");
                player.sendMessage(MiniMessage.miniMessage().deserialize(rawMsg.replace("%prefix%", prefix)));

                updateDiscordMessage(event, "approved");
            } else {
                updateDiscordMessage(event, "player-offline");
            }
        });
    }

    private void handleKick(final ButtonInteractionEvent event, final UUID uuid) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                authService.markStealthKick(uuid);

                final String kickMsg = plugin.getConfigManager().getLocale().getString("messages.auth-kick-reason", "<red>Вход был отклонен вами через Discord.");
                player.kick(MiniMessage.miniMessage().deserialize(kickMsg));

                updateDiscordMessage(event, "kicked");
            } else {
                updateDiscordMessage(event, "kick-failed-offline");
            }
        });
    }

    private void handleBlock(final ButtonInteractionEvent event, final UUID uuid) {
        authService.banUserAsync(uuid).thenRun(() -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                final Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    authService.markStealthKick(uuid);
                    final String kickMsg = plugin.getConfigManager().getLocale().getString("messages.auth-kick-blocked", "<red>Ваш аккаунт заблокирован через Discord.");
                    player.kick(MiniMessage.miniMessage().deserialize(kickMsg));
                }

                updateDiscordMessage(event, "blocked");
            });
        });
    }

    private void updateDiscordMessage(final ButtonInteractionEvent event, final String statusKey) {
        final FileConfiguration locale = plugin.getConfigManager().getLocale();
        final Message message = event.getMessage();
        if (message.getEmbeds().isEmpty()) return;

        final MessageEmbed oldEmbed = message.getEmbeds().get(0);
        final String path = "discord.interaction." + statusKey + ".";
        final EmbedBuilder newEmbed = new EmbedBuilder(oldEmbed)
                .addField(
                        locale.getString("discord.interaction.status-field", "Action status"),
                        locale.getString(path + "value", ""),
                        false
                )
                .setColor(parseColor(locale.getString(path + "color", "#22C55E")));

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

    private Color parseColor(final String colorStr) {
        try {
            return Color.decode(colorStr);
        } catch (IllegalArgumentException e) {
            return Color.decode("#22C55E");
        }
    }
}
