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
