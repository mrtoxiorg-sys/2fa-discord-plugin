# 2FA Discord Plugin

Universal Discord-based 2FA plugin for Paper servers.


## English

### Overview

`2FA Discord Plugin` adds a Discord approval step to Minecraft logins on Paper servers.

After a player links their Minecraft account to Discord, future logins can require confirmation through Discord direct messages. The plugin can also temporarily block player actions while approval is pending, kick a login attempt, or block the account through Discord controls.

### Features

- Discord-based login confirmation
- Account linking with one-time verification codes
- Temporary login session by IP
- Optional account blocklist
- Admin commands for disabling 2FA and unblocking accounts
- Configurable locale system: `en_us` and `ru_ru`
- Database support: `sqlite`, `mysql`, `postgresql`

### Requirements

- Java 21
- Paper `1.21.1+`
- Discord bot token

### Commands

- `/2fa enable` — link your Minecraft account to Discord
- `/2fa disable [player/uuid/discord_id]` — disable 2FA for a player
- `/2fa unblock [player/uuid/discord_id]` — remove a 2FA block
- `/2fa reload` — reload config and services

### Permissions

- `2fa.command.enable`
- `2fa.command.disable`
- `2fa.command.unblock`
- `2fa.command.reload`

### Configuration

Main config: [src/main/resources/config.yml](src/main/resources/config.yml)

Important options:

- `discord.token` — Discord bot token
- `locale` — active locale (`en_us` or `ru_ru`)
- `session-duration-seconds` — how long the IP session stays trusted
- `database.type` — `sqlite`, `mysql`, or `postgresql`

### Locales

Locale files:

- [src/main/resources/locale/en_us.yml](src/main/resources/locale/en_us.yml)
- [src/main/resources/locale/ru_ru.yml](src/main/resources/locale/ru_ru.yml)

Default locale: `en_us`

### Build

```bash
sh gradlew shadowJar
```

Result:

```text
build/libs/2fa-1.0-SNAPSHOT.jar
```

### Installation

1. Build the plugin with `shadowJar`
2. Put the jar into your Paper server `plugins/` folder
3. Start the server once
4. Open the generated `config.yml`
5. Set your Discord bot token
6. Adjust database and locale settings if needed
7. Restart the server

### Disclaimer

This code is provided **as is**, without warranties or guarantees of any kind, express or implied.

The author does not guarantee:

- fitness for a particular purpose
- uninterrupted operation
- compatibility with every server setup
- protection from configuration mistakes or third-party issues

You use, modify, and distribute this code at your own risk.

---

## Русский

### О проекте

`2FA Discord Plugin` добавляет подтверждение входа через Discord для Paper-серверов Minecraft.

После привязки Minecraft-аккаунта к Discord вход на сервер можно подтверждать через личные сообщения Discord. Пока подтверждение не получено, плагин может блокировать действия игрока. Также через Discord можно отклонить вход или заблокировать аккаунт.

### Возможности

- подтверждение входа через Discord
- привязка аккаунта по одноразовому коду
- временная доверенная сессия по IP
- встроенный блоклист аккаунтов
- админ-команды для отключения 2FA и снятия блокировки
- переключаемая локализация: `en_us` и `ru_ru`
- поддержка баз данных: `sqlite`, `mysql`, `postgresql`

### Требования

- Java 21
- Paper `1.21.1+`
- токен Discord-бота

### Команды

- `/2fa enable` — привязать Minecraft-аккаунт к Discord
- `/2fa disable [player/uuid/discord_id]` — отключить 2FA у игрока
- `/2fa unblock [player/uuid/discord_id]` — снять блокировку 2FA
- `/2fa reload` — перезагрузить конфиг и сервисы

### Права

- `2fa.command.enable`
- `2fa.command.disable`
- `2fa.command.unblock`
- `2fa.command.reload`

### Настройка

Основной конфиг: [src/main/resources/config.yml](src/main/resources/config.yml)

Ключевые параметры:

- `discord.token` — токен Discord-бота
- `locale` — активная локаль (`en_us` или `ru_ru`)
- `session-duration-seconds` — срок действия доверенной IP-сессии
- `database.type` — `sqlite`, `mysql` или `postgresql`

### Локализации

Файлы локализаций:

- [src/main/resources/locale/en_us.yml](src/main/resources/locale/en_us.yml)
- [src/main/resources/locale/ru_ru.yml](src/main/resources/locale/ru_ru.yml)

Локаль по умолчанию: `en_us`

### Сборка

```bash
sh gradlew shadowJar
```

Результат:

```text
build/libs/2fa-1.0-SNAPSHOT.jar
```

### Установка

1. Соберите плагин через `shadowJar`
2. Поместите jar-файл в папку `plugins/` вашего Paper-сервера
3. Один раз запустите сервер
4. Откройте сгенерированный `config.yml`
5. Укажите токен Discord-бота
6. При необходимости настройте базу данных и локаль
7. Перезапустите сервер

### Отказ от гарантий

Этот код предоставляется **как есть**, без каких-либо гарантий или обязательств, явных или подразумеваемых.

Автор не гарантирует:

- пригодность для конкретной задачи
- бесперебойную работу
- совместимость с любой серверной сборкой
- защиту от ошибок конфигурации или проблем сторонних сервисов

Вы используете, изменяете и распространяете этот код на свой страх и риск.
