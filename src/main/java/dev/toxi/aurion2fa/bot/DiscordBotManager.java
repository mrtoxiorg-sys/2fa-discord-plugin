/*
 * Created by: [TheToxi_LSD]
 * Edited by: [TheToxi_LSD]
 */
package dev.toxi.aurion2fa.bot;

import dev.toxi.aurion2fa.Aurion2fa;
import dev.toxi.aurion2fa.service.AuthService;
import dev.toxi.aurion2fa.service.CodeGeneratorService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class DiscordBotManager {

    private final Aurion2fa plugin;
    private final AuthService authService;
    private final CodeGeneratorService codeGenerator;

    private JDA jda;

    public DiscordBotManager(final Aurion2fa plugin, final AuthService authService, final CodeGeneratorService codeGenerator) {
        this.plugin = plugin;
        this.authService = authService;
        this.codeGenerator = codeGenerator;
    }

    // Асинхронный запуск JDA бота
    public void start() {
        final String token = plugin.getConfigManager().getConfig().getString("discord.token", "");
        if (token.isEmpty() || token.equalsIgnoreCase("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().severe("Токен Discord-бота не указан или имеет значение по умолчанию! Бот не запущен.");
            return;
        }

        // ИСПРАВЛЕНО: Используем оригинальный пакет до этапа релокации (shading).
        // Сборщик автоматически заменит этот путь на dev.toxi.aurion2fa.libs.jda во время компиляции.
        net.dv8tion.jda.internal.utils.JDALogger.setFallbackLoggerEnabled(false);

        // Запуск в отдельном потоке, чтобы не блокировать загрузку сервера Minecraft
        CompletableFuture.runAsync(() -> {
            try {
                // Используем облегчённую инициализацию createLight без загрузки гильдий
                this.jda = JDABuilder.createLight(token, EnumSet.of(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                        // Отключаем абсолютно все кэш-флаги для минимизации потребления памяти и удаления предупреждений
                        .disableCache(EnumSet.allOf(CacheFlag.class))
                        // Отключаем кэширование и чанкинг участников, так как они не требуются для работы 2FA
                        .setMemberCachePolicy(MemberCachePolicy.NONE)
                        .setChunkingFilter(ChunkingFilter.NONE)
                        .setStatus(OnlineStatus.ONLINE)
                        // Регистрация слушателей событий
                        .addEventListeners(
                                new DiscordMessageListener(plugin, authService, codeGenerator, this),
                                new DiscordButtonListener(plugin, authService)
                        )
                        .build()
                        .awaitReady(); // Ожидаем готовности бота

                plugin.getLogger().info("Discord-бот успешно авторизован под именем: " + jda.getSelfUser().getAsTag());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Критическая ошибка при запуске Discord-бота!", e);
            }
        });
    }

    // Принудительное блокирующее выключение бота с очисткой ресурсов
    public void stop() {
        if (this.jda != null) {
            try {
                // shutdownNow() принудительно останавливает потоки, закрывает вебсокет
                // и отменяет все ожидающие выполнения RestActions
                this.jda.shutdownNow();

                // Ожидаем завершения работы потоков старой сессии (таймаут 10 секунд).
                // Это гарантирует, что старый инстанс не будет параллельно обрабатывать события с новым
                if (!this.jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Превышено время ожидания остановки старого Discord-бота. Некоторые потоки могли остаться активными.");
                } else {
                    plugin.getLogger().info("Сессия Discord-бота полностью закрыта, все фоновые потоки завершены.");
                }
            } catch (InterruptedException e) {
                plugin.getLogger().log(Level.SEVERE, "Процесс остановки Discord-бота был аварийно прерван!", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Асинхронно отправляет личное сообщение пользователю Discord.
     * Возвращает CompletableFuture для отслеживания успеха/ошибки доставки.
     */
    public CompletableFuture<Void> sendPrivateMessage(final String discordUserId, final MessageCreateData messageData) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        if (this.jda == null) {
            future.completeExceptionally(new IllegalStateException("JDA бот не запущен."));
            return future;
        }

        this.jda.retrieveUserById(discordUserId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(messageData).queue(
                        success -> future.complete(null),
                        error -> {
                            plugin.getLogger().log(Level.WARNING, "Не удалось отправить ЛС пользователю с ID: " + discordUserId + " (возможно, закрыто ЛС)", error);
                            future.completeExceptionally(error);
                        }
                );
            }, future::completeExceptionally);
        }, future::completeExceptionally);

        return future;
    }

    public boolean isReady() {
        return this.jda != null && this.jda.getStatus() == JDA.Status.CONNECTED;
    }

    public JDA getJda() {
        return this.jda;
    }
}