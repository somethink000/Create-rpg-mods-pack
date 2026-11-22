# Expedition — сборка NeoForge 1.21.1 вокруг Create

## Как поставить

### Способ 1. Одной командой (рекомендуется)
Команда скачает все моды в отдельную папку `.minecraft/home/Expedition` (с другими вашими модами сборка не смешается), поставит NeoForge и добавит в лаунчер установку **Expedition**.

**Windows** — нажмите `Win + X` → «Терминал» (или «Windows PowerShell») и вставьте:
```
irm https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.ps1 | iex
```

**Linux / macOS** — откройте терминал и вставьте:
```
curl -sL https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.sh | sh
```

Затем в лаунчере:
- **официальный лаунчер** — выберите установку **Expedition** и играйте;
- **TLauncher / Legacy Launcher** — в настройках включите «Отдельная папка для каждой версии», выберите версию **Expedition** и выделите **6 ГБ памяти**.

Сервер: **`212.22.85.153`**. Обновление — та же команда ещё раз.
