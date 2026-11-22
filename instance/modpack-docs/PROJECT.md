# Сборка: Create + корабли + боссы — описание проекта

Обновлено: 2026-11-14. Это обзор всего проекта. Подробности по каждой теме — в соседних файлах (ссылки ниже).

## 1. Что это за сборка

- **Версия:** Minecraft 1.21.1, NeoForge 21.1.265, ~230 модов. Папка игры: `.minecraft/versions/neoforge-21.1.265`.
- **Для кого:** компания друзей, ~1–2 недели игры (один тир ≈ 1–2 вечера).
- **Суть:** индустрия на Create, путешествия на собственных кораблях (Create Aeronautics / Sable), сильные боссы с уникальным лутом, космос, программирование на CC: Tweaked.

### Главные правила
1. **Ничего даром.** Энергия, ресурсы, движение и урон стоят топлива или ресурсов. Бесконечные генераторы и бесплатные механики убираются или делаются дорогими.
2. **Путешествие = прогресс.** Ключевые ресурсы есть только в определённых местах, у боссов или в измерениях. Телепорты и быстрый транспорт — только дорогие.
3. **Личного полёта нет.** Элитры, джетпаки, крылья, полёт-маунты, полёт от зелий и магии запрещены. Летают только на кораблях.
4. **Корабли дорогие и нужные.** Это главный транспорт, и их обслуживание стоит топлива.
5. **Боссы сильные, но награда того стоит.** После 30-минутного боя игрок не должен пожалеть: с каждого босса падает уникальный и полезный предмет, без мусора.
6. **Одна система на одну задачу:** одна сталь, один свинец, одна электрика. Так дорогой рецепт нельзя обойти через соседний мод.

## 2. Состав модов

| Группа | Моды |
|---|---|
| **Create (ядро)** | Create 6.0.10 и ~60 аддонов: Connected, Encased, Copycats+, Deco, Design n' Decor, Dreams & Desires, Bits 'n' Bobs, Steam 'n' Rails (поезда), Railways Navigator, Factory, Compressed, Power Loader, Pattern Schematics, Rock & Stone (месторождения), Slice & Dice, Central Kitchen, Confectionery, Garnished, Food, Interiors, Escalated, Extra Gauges, Rechiseled Create и др. |
| **Индустрия и энергия** | **TFMG Community Edition** (сталь, нефть, электрика, FE) + KubeJS TFMG; **Create Nuclear 2.0** (эндгейм); Rubberworks (резина); Liquid Fuel; Low-Heated; Picky Wheels |
| **Корабли и транспорт** | Create Aeronautics (Sable), Propulsion, Avionics, Gyroscope Stabilizers, Transmission Linkage, Automated Logistics, Deep Seas (подлодки), Simulated Coasters, Tweaked Controllers и мелкие совместимости |
| **Космос** | Create: Cosmonautics (ракеты, Луна, астероиды) + Deep Air (воздух и гравитация, FE) |
| **Оружие** | Create Big Cannons (+ CBC Peripherals, CC:CBC, Military Supplement, Going Ballistic), Gunsmithing + NTGL, Create Missiles, The Air War, Warnautics, Radars, Aero Radar |
| **Компьютеры** | CC: Tweaked, Advanced Peripherals, CC:C Bridge, CCBR, CC Sable, CC Logistics |
| **Боссы и мобы** | L_Ender's Cataclysm, Bosses of Mass Destruction, Bosses'Rise, Qliphoth Awakening, Legendary Monsters, The Graveyard, Mowzie's Mobs, EDF Remastered, Wither Reincarnated, Block Factory's Bosses, Aquamirae, Remnants (мобы сильнее с дистанцией), MultiplayerBosses (HP от числа игроков), полоски HP боссов |
| **Лут и снаряжение** | Artifacts, Relics, Apotheosis (+ Attributes, Enchanting, Spawners), LootJS, Advanced Loot Info |
| **Мир и структуры** | Terralith, Tectonic, YUNG's (весь набор + Cave Biomes), Galosphere, Deeper and Darker, When Dungeons Arise + Seven Seas, Dungeons and Taverns, Towns and Towers, Moog's Voyager/Soaring/End, Structory + Towers, Incendium, Nullscape, Formations, Sky Whale Ship, Underwater Village, Sky Village, Structurify |
| **Измерения** | Twilight Forest (поставлен вручную с CurseForge), The Aether + Deep Aether, Луна/астероиды (Cosmonautics) |
| **Скрипты** | KubeJS (+ Create, MoreJS, PonderJS), LootJS |
| **Интерфейс** | JEI (+ Resources, Professions, Breeding, Effect Descriptions), Jade + Addons, Enchantment Descriptions |
| **Оптимизация** | Sodium + Sodium Extra, Iris + Flywheel compat, ImmediatelyFast, EntityCulling, MoreCulling, Dynamic FPS, FerriteCore, ModernFix, Lithium, Noisium, **C2ME** (генерация ×1.8), Structure Layout Optimizer, AllTheLeaks, Create Better FPS, Clumps, Chunky (прегенерация), spark |
| **Свои моды** | Create Catalyst Wear, NeoForge Alternative Auth (раздел 4) |

Полный разбор аддонов Create с оценками — в [create-addons.md](create-addons.md).

## 3. Что сознательно не ставили или убрали

| Мод | Почему |
|---|---|
| Create Jetpack, Curios Jetpack & Backtank, Stuff 'N Additions | Личный полёт обесценивает корабли |
| Waystones: Sable, Hypertubes | Дешёвый быстрый транспорт |
| Create Ore Excavation, Cobblestone, Easy Stone Generators | Бесконечные ресурсы (генераторы камня лежат в `mods-removed/`) |
| Create: Enchantment Industry | Бесконечное копирование книг и зачарование сверх максимума |
| Create: Structures Arise | Сильно тормозил генерацию: писал ~1 млн строк в лог за 8 минут |
| Create: Diesel Generators, Alloyed, Metalwork, Ironworks | Дублируют нефть и сталь TFMG |
| Create: Better Villagers | Детали Create можно было бы купить, а не произвести |
| Create: Flight Control, Automatics | Автопилот — это задача для CC |
| CBC: Advanced Technologies | Падает вместе с Big Cannons + Sable |
| Crafts & Additions, New Age, Power Grid, Electro Energetics | Электрика одна — TFMG |
| Create: Love & War | Нет официального порта на 1.21.1, дублирует TFMG |
| Lootr, «ключи тиров» | Не понравился / пока не делаем |
| Flowing Fluids | Лагает |
| Aeronautics: Claims | Приваты не нужны |

Отложено: Create: Wizardry (решим вместе с магией); Ornithopter Glider и LazyTick — «может быть позже». Полный журнал — [decisions.md](decisions.md).

## 4. Свои моды

### Create Catalyst Wear 1.0.0
Исходники: `C:\Users\Nikita\Documents\Projects\CreateCatalystWear` (git-репозитория пока нет).
**Зачем:** в Create вентилятор с лавой или огнём плавит предметы бесплатно и бесконечно. Мод делает катализатор расходником.
- Катализатор изнашивается, **пока через него дует работающий вентилятор** (по времени, а не по числу предметов), **в любом месте струи**.
- Время жизни по умолчанию (конфиг `config/catalystwear-server.toml`):

| Катализатор | Время | Что остаётся |
|---|---|---|
| Огонь, огонь душ, обогащающий огонь | 10 мин | гаснет |
| Костры | 20 мин | тухнут, можно зажечь снова |
| Источник воды | 20 мин | испаряется |
| Текущая вода | до 2 мин (тоньше поток — меньше, в квадрате) | плотный лёд |
| Источник лавы | 40 мин | обсидиан |
| Текущая лава | до 2 мин (так же по уровню) | булыжник, перекрывает струю |
| Катализаторы Create: Connected, паруса Dreams & Desires | 40 мин | пустой катализатор / рама |
| Песок, зыбучий песок, рыхлый снег | 20 мин | исчезают |

- Текущая жидкость в струе изнашивает и **свой источник**, даже если тот стоит вне струи (поиск вверх по течению, до 64 блоков). Источник в струе того же вентилятора не изнашивается дважды. Под двумя разными вентиляторами источник изнашивается от обоих.
- Горелки Blaze Burner (и так едят топливо) и голова/яйцо дракона не изнашиваются.
- Проверка: 28 юнит-тестов и игровые тесты на отдельном сервере. Попутно найден и исправлен баг: вода-источник не испарялась.

### NeoForge Alternative Auth 1.0.0
Исходники: `C:\Users\Nikita\Documents\Projects\AlternativeAuth_NeoForge`. Порт Fabric-мода Alternative Authentication на NeoForge. Серверный мод: позволяет входить через сторонние серверы авторизации (например, Ely.by) вместе с Mojang. Конфиг — `config/alternative-auth.json`.

## 5. Изменения правил (KubeJS, датапаки, конфиги)

### Запреты и «одна система»
| Что | Как |
|---|---|
| Личный полёт | `ban_player_flight.js`: удалены рецепты (Soul Elytra, Ignitium Elytra, крылья Create QoL, джетпак и ножные двигатели Cosmonautics и др.); элитры, крылья, джетпаки, Helium Flamingo и Levitation Block удаляются из инвентаря; каждый тик снимается творческий полёт и планирование; с летающих маунтов Aether игрока ссаживает. Оставлены: прыжки Moa. Зонт Artifacts больше не планирует |
| Контрапции на вагонетках | `ban_minecart_contraptions.js`: Cart Assembler и Minecart Coupling без рецептов, ставить нельзя (вместо них — корабли и поезда) |
| Дешёвый FE | `ban_cheap_fe_converters.js`: Industrial Alternator убран. FE получают только через изолятор ЛЭП TFMG |
| Одна сталь | `single_steel_tfmg.js`: дешёвая сталь Gunsmithing и Big Cannons убрана, рецепты переведены на сталь TFMG |
| Один свинец | `single_lead_tfmg.js` + отключена руда свинца Gunsmithing и Create Nuclear |
| Спавнеры | `disable_spawner_modifiers.js`: модификаторы Apothic Spawners отключены. Двигать спавнеры контрапцией нельзя |
| Резина | `rubber_instead_of_kelp.js`: в логичных рецептах ламинария заменена резиной Rubberworks |
| Скрытие в JEI | `client_scripts/hide_banned_items.js` |

### «Ничего даром» — конфиги
- Водяные колёса только у большой реки (Picky Wheels, порог 1024 блока воды), ветряки только на открытом месте. Мощность: колесо 16, большое 64, ветряк 160, паровой двигатель 512.
- Конечная вода (Finite Water: источники появляются только в океанах, реках и болотах, включая биомы Terralith); пассивного нагрева нет (Low-Heated).
- Поезда на топливе (`realisticTrains`), месторождения Rock & Stone конечные и медленнее.
- Горелки кораблей: топливо ×1.75, тяга горячего воздуха 1.5 → 1.2; двигатели Propulsion тратят ×2 топлива, ионный — 150 FE/тик.
- Короче поршни (32) и канаты (128); у Advanced Peripherals выключены сканер руд, черепаха-чанклоадер и черепаха-«лифт».

### Баланс боя (`balance_*.js`)
- **Боссы:** HP ×2, финальные ×2.5, урон ×1.3; взрывы по боссу ×0.35; один удар — не больше 6% HP босса (чтобы пушки и ракеты не убивали за секунды); +70% HP за каждого игрока рядом.
- **Лут боссов** (LootJS): у 42 боссов гарантированный тематический артефакт или реликвия + 35% шанс на второй; мусор убран. Подробно — [loot-artifacts.md](loot-artifacts.md).
- **Патроны и оружие:** патронов за крафт меньше, гильзы и снаряды из стали TFMG, капсюль — отдельный шаг; гатлинг и автооружие требуют стальной механизм TFMG.
- **Атом — эндгейм:** уран и торий генерируются только на Луне, реактор из титановой обшивки Cosmonautics. Титан в Overworld редкий и глубокий, основной — на Луне.

### Прогрессия (план)
T0 старт → T1 латунь (Незер) → T2 сталь TFMG → T3 нефть и электричество → T4 боевой корабль (Энд, мидл-боссы) → T5 космос (титан, Луна) → T6 эндгейм (финальные боссы, атом). Подробно — [balance.md](balance.md).

## 6. Производительность

Замерено на тестовом сервере (spark, Chunky): ~65% времени генерации уходит на рельеф. **C2ME ускорил генерацию в 1,8 раза** (22 → 39 чанков/с) и оставлен насовсем; это альфа, поэтому мир нужно бэкапить. Перед игрой — прегенерация мира через Chunky. Подробно — [performance.md](performance.md).

## 7. Что ещё не сделано

- **Всё изменённое в балансе ещё не проверено в игре**: KubeJS-скрипты загружаются без ошибок, но тестовых боёв с боссами не было.
- Сделать трофеи эксклюзивными: убрать артефакты и реликвии боссов из сундуков.
- Контрапции: тег `non_movable` и лимит размера (проверить с кораблями Sable).
- Выход энергии реактора Create Nuclear.
- Проверить полёт у сета Valkyrie (Aether); решить про Chorus Staff (Relics) и Cloud in a Bottle (Artifacts).
- Проверить лут Sky Village: не даёт ли детали кораблей даром.
- Мод бэкапов мира (нужен из-за C2ME) — не решено.

## 8. Документы

| Файл | О чём |
|---|---|
| [decisions.md](decisions.md) | Журнал решений: что поставили, что нет и почему |
| [create-addons.md](create-addons.md) | Разбор аддонов Create |
| [balance.md](balance.md) | План баланса и применённые изменения |
| [travel-worldgen.md](travel-worldgen.md) | Биомы, структуры, измерения, космос |
| [mobs-bosses.md](mobs-bosses.md) | Мобы, боссы, огнестрел |
| [loot-artifacts.md](loot-artifacts.md) | Лут боссов и артефакты |
| [contraptions.md](contraptions.md) | Контрапции и замены из Aeronautics |
| [free-mechanics.md](free-mechanics.md) | Бесплатные механики и как их закрыли |
| [performance.md](performance.md) | Генерация, FPS, замеры |
