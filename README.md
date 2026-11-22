# Expedition — сборка NeoForge 1.21.1 вокруг Create

## Как поставить
1. Установите [Prism Launcher](https://prismlauncher.org/) или [Modrinth App](https://modrinth.com/app).
2. Скачайте `Expedition-<версия>.mrpack` из раздела **Releases** этого репозитория.
3. В лаунчере: «Добавить сборку» → «Импорт» → выберите файл (или вставьте ссылку на него). Моды скачаются сами.

Обновление — так же: импортируйте `.mrpack` новой версии.

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
