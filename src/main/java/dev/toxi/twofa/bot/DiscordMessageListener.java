/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.bot;

import dev.toxi.twofa.TwoFactorPlugin;
import dev.toxi.twofa.service.AuthService;
import dev.toxi.twofa.service.CodeGeneratorService;
import net.dv8tion.jda.api.entities.channel.ChannelType; // Измененный путь импорта в JDA 5.x
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.regex.Pattern;

public final class DiscordMessageListener extends ListenerAdapter {

    private final TwoFactorPlugin plugin;
    private final AuthService authService;
    private final CodeGeneratorService codeGenerator;
    private final DiscordBotManager botManager;

    // Регулярное выражение для поиска ровно 4 цифр
    private final Pattern codePattern = Pattern.compile("^[0-9]{4}$");

    public DiscordMessageListener(final TwoFactorPlugin plugin, final AuthService authService, final CodeGeneratorService codeGenerator, final DiscordBotManager botManager) {
        this.plugin = plugin;
        this.authService = authService;
        this.codeGenerator = codeGenerator;
        this.botManager = botManager;
    }

    @Override
    public void onMessageReceived(final @NotNull MessageReceivedEvent event) {
        // Игнорируем сообщения от ботов и сообщения из публичных каналов гильдий
        if (event.getAuthor().isBot() || !event.isFromType(ChannelType.PRIVATE)) {
            return;
        }

        final String rawContent = event.getMessage().getContentRaw().trim();
        if (!codePattern.matcher(rawContent).matches()) {
            return; // Сообщение не является 4-значным кодом
        }

        final String discordId = event.getAuthor().getId();

        // Проверяем, существует ли этот код в кэше генератора
        codeGenerator.consumeCode(rawContent).ifPresentOrElse(uuid -> {
            // Код валиден -> Связываем аккаунты асинхронно
            authService.linkUserAsync(uuid, discordId).thenAccept(success -> {
                if (success) {
                    // 1. Отправляем успешный Embed в Discord DM
                    final MessageCreateData successEmbed = DiscordEmbedService.createSimpleEmbed(plugin, "link-success");
                    event.getChannel().sendMessage(successEmbed).queue();

                    // 2. Отправляем фидбек игроку в чат Minecraft, если он онлайн (в основном потоке)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        final Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline()) {
                            final String prefix = plugin.getConfigManager().getLocale().getString("prefix", "");
                            final String rawMsg = plugin.getConfigManager().getLocale().getString("messages.link-success", "%prefix%<green>Success:</green> <gray>Your account is now linked to Discord.</gray>");
                            player.sendMessage(MiniMessage.miniMessage().deserialize(rawMsg.replace("%prefix%", prefix)));
                        }
                    });
                } else {
                    event.getChannel().sendMessage(
                            plugin.getConfigManager().getLocale().getString(
                                    "discord.messages.database-save-error",
                                    "There was an error while saving your Minecraft account data. Please contact an administrator."
                            )
                    ).queue();
                }
            });
        }, () -> {
            // Код не найден или истёк
            final MessageCreateData errorEmbed = DiscordEmbedService.createSimpleEmbed(plugin, "link-expired");
            event.getChannel().sendMessage(errorEmbed).queue();
        });
    }
}
