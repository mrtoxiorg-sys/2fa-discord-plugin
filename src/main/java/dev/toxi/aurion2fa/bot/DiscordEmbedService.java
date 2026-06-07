/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.bot;

import dev.toxi.aurion2fa.Aurion2fa;
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
            return Color.decode("#4FD6FF"); // Дефолтный голубой цвет
        }
    }

    /**
     * Создает Embed-запрос на авторизацию 2FA (Вход на сервер) с интерактивными кнопками.
     */
    public static MessageCreateData createAuthRequest(final Aurion2fa plugin, final UUID uuid, final String playerName, final String ip) {
        final FileConfiguration locale = plugin.getConfigManager().getDiscordLocale();

        final String path = "auth-request.";
        final String title = locale.getString(path + "title", "Попытка входа на сервер")
                .replace("%player%", playerName);
        final String desc = locale.getString(path + "description", "Игрок **%player%** пытается зайти под вашим аккаунтом.\n\n**IP-Адрес:** `%ip%`")
                .replace("%player%", playerName)
                .replace("%ip%", ip);
        final Color color = parseColor(locale.getString(path + "color", "#FF9F4F"));

        final EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(desc)
                .setColor(color)
                .setTimestamp(java.time.Instant.now());

        // Названия кнопок из конфига
        final String btnApprove = locale.getString(path + "buttons.approve", "Одобрить вход");
        final String btnKick = locale.getString(path + "buttons.kick", "Кикнуть");
        final String btnBlock = locale.getString(path + "buttons.block", "Заблокировать");

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
    public static MessageCreateData createSimpleEmbed(final Aurion2fa plugin, final String configPath, final String... placeholders) {
        final FileConfiguration locale = plugin.getConfigManager().getDiscordLocale();

        String title = locale.getString(configPath + ".title", "Уведомление");
        String desc = locale.getString(configPath + ".description", "");
        final Color color = parseColor(locale.getString(configPath + ".color", "#4FD6FF"));

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