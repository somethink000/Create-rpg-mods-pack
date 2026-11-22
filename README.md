# Expedition — сборка NeoForge 1.21.1 вокруг Create

## Как поставить

### Способ 1. Одной командой (рекомендуется)
Команда скачает все моды и сборку в папку игры `.minecraft` (её используют TLauncher и обычный лаунчер Minecraft). Если в папке уже были моды, они сохранятся в `mods-before-expedition`.

**Windows** — нажмите `Win + X` → «Терминал» (или «Windows PowerShell») и вставьте:
```
irm https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.ps1 | iex
```

**Linux / macOS** — откройте терминал и вставьте:
```
curl -sL https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.sh | sh
```

Затем в лаунчере выберите версию **NeoForge 1.21.1** (если её нет — установите её там же), выделите игре **6–8 ГБ памяти** и запустите.

### Способ 2. Через Prism Launcher (Windows, Linux, macOS)
1. Установите **[Prism Launcher](https://prismlauncher.org/download/)**.
2. Скачайте **[Expedition.mrpack](https://github.com/somethink000/Create-rpg-mods-pack/releases/latest/download/Expedition.mrpack)**.
3. Перетащите файл в окно Prism и нажмите **OK** — моды скачаются сами, NeoForge тоже.

### Сервер
«Сетевая игра» → «Добавить» → адрес **`212.22.85.153`**. Ник любой.

### Обновление
Вышла новая версия — повторите ту же команду (способ 1) или импортируйте новый `.mrpack` (способ 2).

## Что в репозитории
Здесь лежит всё, из чего сделана сборка, кроме модов с Modrinth: они скачиваются лаунчером по ссылкам из `.mrpack`. Моды, которых на Modrinth нет (ядро `expedition`, порт атласа, Catalyst Wear, Twilight Forest, AllTheLeaks), лежат в `instance/mods/`.

Выпуск версии: `./release.sh 0.2.0 "что изменилось"` — собирает `.mrpack`, публикует релиз на GitHub и обновляет сервер (`deploy-vps.sh`).

| Папка | Что это |
|---|---|
| `instance/` | Сама сборка: `mods/`, `config/`, `kubejs/`, `defaultconfigs/`, миры (`saves/`). Лаунчер (TLauncher в Wine) открывает её по старому пути `~/.wine/.../game/home/NeoForge 1.21.1` — это ссылка на эту папку. |
| `instance/modpack-docs/` | Документация и история решений (`roadmap.md`). |
| `core/` | Мод-ядро `expedition`: маршрут и атлас-вкладка, гарнизоны, NG+, лут, единое размещение данжей, баланс снаряжения, рецепты прогрессии. |
| `atlas/` | Порт Antique Atlas 3.1.2 + Surveyor 1.2.4 на NeoForge (LGPL). |
| `testserver/` | Тестовый сервер: `./run.sh nogui`, `tools/rcon.py "команда"`, `tools/sync-pack.sh` — скопировать сборку из `instance/`. |

## Частые действия
- Собрать и разложить моды: `./deploy.sh` (только ядро: `./deploy.sh core`).
- Игровые тесты ядра: `cd core && ./gradlew runGameTestServer`.
- Генераторы данных ядра: `core/tools/audit/gen_progression.py` (рецепты прогрессии), `core/tools/audit/gen_create_gear.py` + `convert_recipes.py` (снаряжение через Create), `core/tools/art/tint.py` (текстуры).
- Команды на сервере: `/expedition route testall <pos>`, `/expedition garrison scan <pos>`, `/expedition audit gear|loot`.

Java 21: `/usr/lib/jvm/java-21-openjdk`.
