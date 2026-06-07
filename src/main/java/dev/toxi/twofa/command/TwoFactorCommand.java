/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.command;

import dev.toxi.twofa.TwoFactorPlugin;
import dev.toxi.twofa.bot.DiscordEmbedService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
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

public final class TwoFactorCommand implements CommandExecutor, TabCompleter {

    private static final String ENABLE_PERMISSION = "2fa.command.enable";
    private static final String DISABLE_PERMISSION = "2fa.command.disable";
    private static final String UNBLOCK_PERMISSION = "2fa.command.unblock";
    private static final String RELOAD_PERMISSION = "2fa.command.reload";

    private final TwoFactorPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TwoFactorCommand(final TwoFactorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command, @NotNull final String label, @NotNull final String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        final String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "enable", "link" -> handleEnable(sender);
            case "disable", "unlink" -> handleDisable(sender, args);
            case "unblock", "unban" -> handleUnblock(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleEnable(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendLocaleMessage(sender, "messages.only-players");
            return;
        }

        if (!player.hasPermission(ENABLE_PERMISSION)) {
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

            sendLocaleMessage(player, "messages.link-code-received", "%code%", code);
        });
    }

    private void handleDisable(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(DISABLE_PERMISSION)) {
            sendLocaleMessage(sender, "messages.no-permission");
            return;
        }

        if (args.length < 2) {
            sendLocaleMessage(sender, "commands.usage.disable");
            return;
        }

        final String target = args[1];
        sendLocaleMessage(sender, "commands.progress.disable-search");

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
                            sendLocaleMessage(sender, "commands.result.disable-success-uuid");
                            optDiscord.ifPresent(discordId -> sendDiscordNotification(discordId, "unlink-notify"));
                        } else {
                            sendLocaleMessage(sender, "commands.result.disable-failed");
                        }
                    });
                });
            } else if (resolvedDiscordId != null) {
                final String finalDiscordId = resolvedDiscordId;
                plugin.getAuthService().unlinkUserByDiscordIdAsync(finalDiscordId).thenAccept(success -> {
                    if (success) {
                        sendLocaleMessage(sender, "commands.result.disable-success-discord");
                        sendDiscordNotification(finalDiscordId, "unlink-notify");
                    } else {
                        sendLocaleMessage(sender, "commands.result.disable-failed");
                    }
                });
            } else {
                sendLocaleMessage(sender, "commands.result.target-invalid");
            }
        });
    }

    private void handleUnblock(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission(UNBLOCK_PERMISSION)) {
            sendLocaleMessage(sender, "messages.no-permission");
            return;
        }

        if (args.length < 2) {
            sendLocaleMessage(sender, "commands.usage.unblock");
            return;
        }

        final String target = args[1];
        sendLocaleMessage(sender, "commands.progress.unblock-search");

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
                        sendLocaleMessage(sender, "commands.result.unblock-success-uuid");
                        plugin.getAuthService().getDiscordIdAsync(finalUuid).thenAccept(optDiscord ->
                                optDiscord.ifPresent(discordId -> sendDiscordNotification(discordId, "unban-notify"))
                        );
                    } else {
                        sendLocaleMessage(sender, "commands.result.unblock-failed");
                    }
                });
            } else if (resolvedDiscordId != null) {
                final String finalDiscordId = resolvedDiscordId;
                plugin.getAuthService().unbanUserByDiscordIdAsync(finalDiscordId).thenAccept(success -> {
                    if (success) {
                        sendLocaleMessage(sender, "commands.result.unblock-success-discord");
                        sendDiscordNotification(finalDiscordId, "unban-notify");
                    } else {
                        sendLocaleMessage(sender, "commands.result.unblock-failed");
                    }
                });
            } else {
                sendLocaleMessage(sender, "commands.result.target-invalid-short");
            }
        });
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
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

    private void sendLocaleMessage(final CommandSender sender, final String path, final String... placeholders) {
        final FileConfiguration locale = plugin.getConfigManager().getLocale();
        final String prefix = locale.getString("prefix", "");
        String rawMessage = locale.getString(path, "");
        if (rawMessage.isEmpty()) return;

        rawMessage = applyPlaceholders(rawMessage.replace("%prefix%", prefix), placeholders);
        sender.sendMessage(miniMessage.deserialize(rawMessage));
    }

    private void sendHelp(final CommandSender sender) {
        final List<String> lines = new ArrayList<>();
        final FileConfiguration locale = plugin.getConfigManager().getLocale();
        lines.add(locale.getString("commands.help.header", "<green>[2FA]</green> <gray>Available commands:</gray>"));
        if (sender.hasPermission(ENABLE_PERMISSION) && sender instanceof Player) {
            lines.add(locale.getString("commands.help.enable", "<gray>- /2fa enable</gray>"));
        }
        if (sender.hasPermission(DISABLE_PERMISSION)) {
            lines.add(locale.getString("commands.help.disable", "<gray>- /2fa disable [player/uuid/discord_id]</gray>"));
        }
        if (sender.hasPermission(UNBLOCK_PERMISSION)) {
            lines.add(locale.getString("commands.help.unblock", "<gray>- /2fa unblock [player/uuid/discord_id]</gray>"));
        }
        if (sender.hasPermission(RELOAD_PERMISSION)) {
            lines.add(locale.getString("commands.help.reload", "<gray>- /2fa reload</gray>"));
        }

        lines.forEach(line -> sender.sendMessage(miniMessage.deserialize(line)));
    }

    private String applyPlaceholders(String input, final String... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            input = input.replace(placeholders[i], placeholders[i + 1]);
        }
        return input;
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
            if (sender.hasPermission(ENABLE_PERMISSION) && sender instanceof Player) completions.add("enable");
            if (sender.hasPermission(DISABLE_PERMISSION)) completions.add("disable");
            if (sender.hasPermission(UNBLOCK_PERMISSION)) completions.add("unblock");
            if (sender.hasPermission(RELOAD_PERMISSION)) completions.add("reload");
            return filterCompletions(completions, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> filterCompletions(final List<String> list, final String input) {
        final String lowerInput = input.toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(lowerInput)).toList();
    }
}
