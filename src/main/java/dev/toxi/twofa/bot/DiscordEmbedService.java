/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.twofa.bot;

import dev.toxi.twofa.TwoFactorPlugin;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.Color;
import java.util.UUID;

public final class DiscordEmbedService {

    // Вспомогательный метод парсинга HEX-цветов
    private static Color parseColor(final String colorStr) {
        try {
            return Color.decode(colorStr);
        } catch (NumberFormatException e) {
            return Color.decode("#22C55E");
        }
    }

    /**
     * Создает Embed-запрос на авторизацию 2FA (Вход на сервер) с интерактивными кнопками.
     */
    public static MessageCreateData createAuthRequest(final TwoFactorPlugin plugin, final UUID uuid, final String playerName, final String ip) {
        final FileConfiguration locale = plugin.getConfigManager().getLocale();

        final String path = "discord.auth-request.";
        final String title = locale.getString(path + "title", "Login attempt")
                .replace("%player%", playerName);
        final String desc = locale.getString(path + "description", "Player **%player%** is trying to log into your account.\n\n**IP Address:** `%ip%`")
                .replace("%player%", playerName)
                .replace("%ip%", ip);
        final Color color = parseColor(locale.getString(path + "color", "#22C55E"));

        final EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(desc)
                .setColor(color)
                .setTimestamp(java.time.Instant.now());

        // Названия кнопок из конфига
        final String btnApprove = locale.getString(path + "buttons.approve", "Approve");
        final String btnKick = locale.getString(path + "buttons.kick", "Kick");
        final String btnBlock = locale.getString(path + "buttons.block", "Block");

        // Упаковываем кнопки с уникальными ID, несущими информацию о UUID игрока
        final ActionRow actionRow = ActionRow.of(
                Button.success("approve:" + uuid, btnApprove),
                Button.secondary("kick:" + uuid, btnKick),
                Button.danger("block:" + uuid, btnBlock)
        );

        return new MessageCreateBuilder()
                .setEmbeds(embed.build())
                .setComponents(actionRow)
                .build();
    }

    /**
     * Одноцветный информационный Embed (например, успешная привязка, бан и т.д.)
     */
    public static MessageCreateData createSimpleEmbed(final TwoFactorPlugin plugin, final String configPath, final String... placeholders) {
        final FileConfiguration locale = plugin.getConfigManager().getLocale();
        final String path = "discord.embeds." + configPath + ".";

        String title = locale.getString(path + "title", "Notification");
        String desc = locale.getString(path + "description", "");
        final Color color = parseColor(locale.getString(path + "color", "#22C55E"));

        // Замена плейсхолдеров (передаются парами: ключ, значение, ключ, значение...)
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                final String target = placeholders[i];
                final String replacement = placeholders[i + 1];
                title = title.replace(target, replacement);
                desc = desc.replace(target, replacement);
            }
        }

        final EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(desc)
                .setColor(color)
                .setTimestamp(java.time.Instant.now());

        return new MessageCreateBuilder()
                .setEmbeds(embed.build())
                .build();
    }
}
