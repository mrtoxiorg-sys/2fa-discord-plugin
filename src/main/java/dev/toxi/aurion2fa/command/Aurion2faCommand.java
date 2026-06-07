/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.command;

import dev.toxi.aurion2fa.Aurion2fa;
import dev.toxi.aurion2fa.bot.DiscordEmbedService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Aurion2faCommand implements CommandExecutor, TabCompleter {

    private final Aurion2fa plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Aurion2faCommand(final Aurion2fa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label, @NotNull final String[] args) {
        if (args.length == 0) {
            sendLocaleMessage(sender, "messages.no-permission");
            return true;
        }

        final String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "link" -> handleLink(sender);
            case "unlink" -> handleUnlink(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendLocaleMessage(sender, "messages.player-not-found");
        }

        return true;
    }

    private void handleLink(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendLocaleMessage(sender, "messages.only-players");
            return;
        }

        if (!player.hasPermission("aurion2fa.command.link")) {
            sendLocaleMessage(player, "messages.no-permission");
            return;
        }

        final UUID uuid = player.getUniqueId();

        // Асинхронно проверяем, нет ли уже связи в БД
        plugin.getAuthService().getDiscordIdAsync(uuid).thenAccept(optDiscord -> {
            if (optDiscord.isPresent()) {
                sendLocaleMessage(player, "messages.already-linked");
                return;
            }

            // Генерируем 4-значный код (синхронизировано в памяти)
            final String code = plugin.getCodeGeneratorService().generateCode(uuid);

            final String prefix = plugin.getConfigManager().getPluginLocale().getString("prefix", "");
            final String rawMsg = plugin.getConfigManager().getPluginLocale().getString("messages.link-code-received", "%prefix%<gradient:#4FD6FF:#D7F4FA>Инфо →</gradient> <white>Ваш код привязки: <gradient:#4FD6FF:#D7F4FA><bold>%code%</bold></gradient>. Напишите его боту в ЛС!");
            player.sendMessage(miniMessage.deserialize(rawMsg.replace("%prefix%", prefix).replace("%code%", code)));
        });
    }

    private void handleUnlink(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("aurion2fa.command.unlink")) {
            sendLocaleMessage(sender, "messages.no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Использование: /aurion2fa unlink <player/uuid/discord_id>"));
            return;
        }

        final String target = args[1];
        sender.sendMessage(miniMessage.deserialize("<yellow>Выполняется асинхронный поиск и отвязка..."));

        CompletableFuture.runAsync(() -> {
            UUID resolvedUuid = null;
            String resolvedDiscordId = null;

            if (isUuid(target)) {
                resolvedUuid = UUID.fromString(target);
            } else if (target.matches("^\\d{17,20}$")) { // Выглядит как Discord ID
                resolvedDiscordId = target;
            } else {
                // Пытаемся получить UUID офлайн-игрока по имени (в асинхронном потоке!)
                final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(target);
                if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                    resolvedUuid = offlinePlayer.getUniqueId();
                }
            }

            if (resolvedUuid != null) {
                final UUID finalUuid = resolvedUuid;
                // Получаем Discord ID перед удалением, чтобы отправить уведомление
                plugin.getAuthService().getDiscordIdAsync(finalUuid).thenAccept(optDiscord -> {
                    plugin.getAuthService().unlinkUserAsync(finalUuid).thenAccept(success -> {
                        if (success) {
                            sender.sendMessage(miniMessage.deserialize("<green>Привязка 2FA успешно удалена по UUID."));
                            optDiscord.ifPresent(discordId -> sendDiscordNotification(discordId, "unlink-notify"));
                        } else {
                            sender.sendMessage(miniMessage.deserialize("<red>Связь 2FA не найдена или не удалось удалить."));
                        }
                    });
                });
            } else if (resolvedDiscordId != null) {
                final String finalDiscordId = resolvedDiscordId;
                plugin.getAuthService().unlinkUserByDiscordIdAsync(finalDiscordId).thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(miniMessage.deserialize("<green>Привязка 2FA успешно удалена по Discord ID."));
                        sendDiscordNotification(finalDiscordId, "unlink-notify");
                    } else {
                        sender.sendMessage(miniMessage.deserialize("<red>Связь 2FA не найдена или не удалось удалить."));
                    }
                });
            } else {
                sender.sendMessage(miniMessage.deserialize("<red>Не удалось распознать цель (игрок никогда не играл или ID невалиден)."));
            }
        });
    }

    private void handleUnban(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("aurion2fa.command.unban")) {
            sendLocaleMessage(sender, "messages.no-permission");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<red>Использование: /aurion2fa unban <player/uuid/discord_id>"));
            return;
        }

        final String target = args[1];
        sender.sendMessage(miniMessage.deserialize("<yellow>Выполняется асинхронный поиск и разблокировка..."));

        CompletableFuture.runAsync(() -> {
            UUID resolvedUuid = null;
            String resolvedDiscordId = null;

            if (isUuid(target)) {
                resolvedUuid = UUID.fromString(target);
            } else if (target.matches("^\\d{17,20}$")) {
                resolvedDiscordId = target;
            } else {
                final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(target);
                if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                    resolvedUuid = offlinePlayer.getUniqueId();
                }
            }

            if (resolvedUuid != null) {
                final UUID finalUuid = resolvedUuid;
                plugin.getAuthService().unbanUserAsync(finalUuid).thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(miniMessage.deserialize("<green>Аккаунт разблокирован по UUID."));
                        plugin.getAuthService().getDiscordIdAsync(finalUuid).thenAccept(optDiscord ->
                                optDiscord.ifPresent(discordId -> sendDiscordNotification(discordId, "unban-notify"))
                        );
                    } else {
                        sender.sendMessage(miniMessage.deserialize("<red>Блокировка не найдена или не удалось снять."));
                    }
                });
            } else if (resolvedDiscordId != null) {
                final String finalDiscordId = resolvedDiscordId;
                plugin.getAuthService().unbanUserByDiscordIdAsync(finalDiscordId).thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(miniMessage.deserialize("<green>Аккаунт разблокирован по Discord ID."));
                        sendDiscordNotification(finalDiscordId, "unban-notify");
                    } else {
                        sender.sendMessage(miniMessage.deserialize("<red>Блокировка не найдена или не удалось снять."));
                    }
                });
            } else {
                sender.sendMessage(miniMessage.deserialize("<red>Не удалось распознать цель."));
            }
        });
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission("aurion2fa.command.reload")) {
            sendLocaleMessage(sender, "messages.no-permission");
            return;
        }

        // ИСПРАВЛЕНО: Теперь вызывается глобальная перезагрузка с перезапуском сервисов
        plugin.reloadPlugin();
        sendLocaleMessage(sender, "messages.configs-reloaded");
    }

    private void sendDiscordNotification(final String discordId, final String localePath) {
        final var messageData = DiscordEmbedService.createSimpleEmbed(plugin, localePath);
        plugin.getDiscordBotManager().sendPrivateMessage(discordId, messageData);
    }

    private void sendLocaleMessage(final CommandSender sender, final String path) {
        final String prefix = plugin.getConfigManager().getPluginLocale().getString("prefix", "");
        String rawMessage = plugin.getConfigManager().getPluginLocale().getString(path, "");
        if (rawMessage.isEmpty()) return;

        rawMessage = rawMessage.replace("%prefix%", prefix);
        sender.sendMessage(miniMessage.deserialize(rawMessage));
    }

    private boolean isUuid(final String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>();
            if (sender.hasPermission("aurion2fa.command.link")) completions.add("link");
            if (sender.hasPermission("aurion2fa.command.unlink")) completions.add("unlink");
            if (sender.hasPermission("aurion2fa.command.unban")) completions.add("unban");
            if (sender.hasPermission("aurion2fa.command.reload")) completions.add("reload");
            return filterCompletions(completions, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> filterCompletions(final List<String> list, final String input) {
        final String lowerInput = input.toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(lowerInput)).toList();
    }
}