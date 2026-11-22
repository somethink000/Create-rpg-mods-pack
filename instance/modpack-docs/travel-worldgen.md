# Путешествия: биомы, структуры, измерения, космос

Источник: Modrinth API, 2026-11-06, фильтр NeoForge + 1.21.1. «Обновлён» — дата последнего обновления проекта (не обязательно файла под 1.21.1; точную версию проверю при установке).
Цель: **каждое место должно давать причину туда лететь** — уникальный ресурс, лут, босс или вид. Вопрос «зачем лететь» решаем LootJS/KubeJS: ключевые детали — только из конкретных мест.

Легенда: ✅ рекомендую · 🤔 решить вместе · ❌ не советую · 🟠 только CurseForge (ставишь сам)

## 📌 Решения (2026-11-06)

**✅ Установлено:** Create: Cosmonautics, Cosmonautics: Deep Air, Dimensional Sable (встроен в Cosmonautics — отдельно не ставится, иначе будет дубль), Tectonic, Terralith, Lithostitched, YUNG's Cave Biomes, Galosphere, Deeper and Darker (с Otherside), YUNG's набор (API, Dungeons, Mineshafts, Strongholds, Desert/Jungle Temples, Witch Huts, Ocean Monuments, Nether Fortresses, End Island, Bridges, Extras), Dungeons and Taverns, Towns and Towers, Moog's Voyager Structures, Moog's Soaring Structures, Sky Whale Ship, Structory + Towers, Aquamirae, Underwater Village, Incendium, Formations Nether, Nullscape, Moog's End Structures, The Aether + Deep Aether, Structurify, Twilight Forest (вручную).
Зависимости: owo-lib, Fragmentum, GeckoLib, Formations, Moog's Structure Lib, YACL, Cristel Lib.

**🔜 Возможно поставим — обдумать:** Biomes O' Plenty, Alex's Caves (unofficial port), Underground Worlds, Unnamed Sea, Eternal Starlight, The Undergarden, The Bumblezone, The Sift.

**Не выбраны** (остальное из таблиц ниже) — не ставим, пока не вернёмся к вопросу.

---

## 🚀 1. Космос + Create

| Мод | Скачиваний | Обновлён | Что это | Вердикт |
|---|---|---|---|---|
| ✅ **Установлен** — **Create: Cosmonautics** (`rocketnautics`) | 117 тыс. | 2026-08 | Ракетные двигатели для **кораблей Sable/Aeronautics**, орбитальная механика, космическое измерение, Луна, астероиды с ресурсами, зонд «Спутник» с программированием на Lua | ✅ **Главный кандидат.** Ракета — это твой корабль Aeronautics, а не отдельный предмет. Lua-зонд — подарок для друга с CC. Требует Aeronautics + Sable 2.0.3+, встраивает Dimensional Sable |
| Cosmonautics: Deep Air | 77 тыс. | 2026-06 | Воздух и гравитация внутри герметичного корабля (через систему Deep Seas) | ✅ вместе с Cosmonautics. Нужен Deep Seas (стоит) **и FE-генерация**: Crafts & Additions / New Age / Power Grid или любой FE-мод. У TFMG есть блок экспорта FE — надо проверить, хватит ли его |
| Ad Astra | 6 млн | 2026-09 | Классический космос: 4 тира ракет, Луна, Марс, Венера, Меркурий, Glacio, скафандры, кислород, своя техника и кабели | 🤔 Самый наполненный по планетам, но ракеты — **не корабли Sable**, своя параллельная техника. Можно как «планеты-контент», но будут две системы ракет |
| Creating Space | 76 тыс. | 2026-10 | Ракеты из контрапций Create, реалистичная наука, другие планеты | 🤔 Тоже Create-ракеты, но на контрапциях Create, не на Sable. Конфликтует по смыслу с Cosmonautics |
| Stellaris | 202 тыс. | 2026-07 | Модульные ракеты, планеты, галактики | ❌ Своя техника, не связан с Create |
| Dimensional Sable | 42 тыс. | 2026-10 | Перенос кораблей Sable между измерениями | ✅ Уже встроен в Cosmonautics. Важно: без него корабль не уйдёт в другое измерение |

**Совет:** Cosmonautics (+ Deep Air). Если окажется мало планет — подумать про Ad Astra только ради планет.

---

## 🌄 2. Рельеф Overworld — чтобы было что облетать

| Мод | Скачиваний | Обновлён | Что это | Вердикт |
|---|---|---|---|---|
| **Tectonic** | 17,5 млн | 2026-10 | Огромные горы, каньоны, глубокие океаны, реки | ✅ Лучший рельеф для кораблей — горные хребты как препятствия |
| **Terralith** | 24,6 млн | 2026-09 | ~100 биомов на ванильных блоках, без новых предметов | ✅ Совместим с Tectonic (у Tectonic есть режим для Terralith) |
| Lithostitched | 25 млн | 2026-10 | Библиотека совместимости worldgen | ✅ Зависимость/помощник для Tectonic |
| Biomes O' Plenty | 38,6 млн | 2026-11 | 50+ биомов, новые деревья и блоки | 🤔 Хорошо, но вместе с Terralith мир станет «пёстрым». Даёт много деревьев для Rubberworks (сок из BOP-деревьев уже поддержан) |
| Oh The Biomes We've Gone | 12 млн | 2026-10 | 50+ биомов | 🤔 Альтернатива BOP, брать только один |
| Regions Unexplored | 10 млн | 2026-10 | Биомы Overworld и Nether | 🤔 Альтернатива BOP |
| Nature's Spirit | 10,8 млн | 2026-10 | Биомы + блоки | 🤔 Альтернатива |
| WWOO, Geophilic | 2–11 млн | 2026-09 | Переделка ванильных биомов | ❌ Конфликтуют с Terralith по смыслу |
| The Lost Cities | 11,5 млн | 2026-10 | Мир из заброшенных городов | ❌ Другой жанр |

**Совет:** Tectonic + Terralith + Lithostitched. Один биомный мод сверху (BOP) — по желанию.

---

## 🕳 3. Пещеры

| Мод | Обновлён | Что это | Вердикт |
|---|---|---|---|
| YUNG's Cave Biomes | 2026-08 | Ванильные+ пещерные биомы | ✅ |
| Galosphere | 2026-06 | Пещерные биомы, мобы, кристаллы | ✅ |
| Deeper and Darker | 2026-06 | Расширение Deep Dark + измерение Otherside | ✅ Хорошо сочетается с Create: Deep Dark (стоит) |
| Alex's Caves (unofficial port) | 2026-04 | 5 редких пещерных биомов с уникальным контентом | 🤔 Очень крутой контент, но это **неофициальный порт** — риск багов |
| Underground Worlds | 2026-09 | Подземные биомы и данжи | 🤔 |

---

## 🏛 4. Структуры Overworld

| Мод | Скачиваний | Обновлён | Что это | Вердикт |
|---|---|---|---|---|
| **YUNG's набор** (Dungeons, Mineshafts, Strongholds, Desert/Jungle Temples, Witch Huts, Ocean Monuments, Bridges, Extras) | 13–23 млн | 2026-09 | Переделка ванильных структур | ✅ Стандарт качества |
| **Dungeons and Taverns** | 22 млн | 2026-10 | Данжи, таверны, точки интереса | ✅ |
| **When Dungeons Arise** | 11,4 млн | 2025-10 ⚠ | Огромные roguelike-данжи, в т.ч. **летающие** | ✅ Есть воздушные структуры — цель для кораблей |
| **Towns and Towers** | 18,7 млн | 2026-10 | Новые деревни, аванпосты, корабли | ✅ |
| **Moog's Voyager Structures** | 9 млн | 2026-09 | 130+ ванильных структур | ✅ |
| **Moog's Soaring Structures** | 3,7 млн | 2026-09 | **Летающие острова** с лутом, мобами и данжами | ✅ Идеально под корабли |
| Sky Whale Ship | 0,8 млн | 2025-11 | Структуры-киты в небе | ✅ Небо станет живым |
| Structory (+ Towers) | 9 млн | 2026-09 | Атмосферные структуры с лором | ✅ |
| Explorify | 9,9 млн | 2026-05 | Простые ванильные структуры | 🤔 Пересекается с Moog's |
| Repurposed Structures | 5,5 млн | 2026-10 | Варианты ванильных структур по биомам | 🤔 |
| IDAS (Integrated Dungeons and Structures) | 1,2 млн | 2026-06 | Очень детальные данжи | 🤔 Тяжёлые, но красивые |
| Additional Structures | 5,3 млн | 2025-12 | 200+ структур | 🤔 Много мелочи |
| Better Archeology | 7,5 млн | 2026-10 | Археология, артефакты | 🤔 |
| CTOV (ChoiceTheorem's Village) | 10,9 млн | 2026-11 | Новые деревни под биомы | 🤔 Пересекается с Towns and Towers |
| Let the Adventure Begin / Pillager Arise / Sky Village / Structures Overhaul / Easy Structures / New Beginnings | — | — | Create-структуры | ✅ уже стоят (Structures Arise убран — тормозит генерацию) |
| Create: Rustic Structures | 2026-05 | Функциональные Create-структуры | 🤔 Проверить, не даёт ли готовых механизмов |
| Dynamic Village | 2026-06 | Create-жители и структуры | 🤔 Похоже на Better Villagers — торговля Create-деталями |

**Важно:** каждый мод структур увеличивает время генерации. Structure Layout Optimizer уже стоит. Плотность регулируем через **Structurify** (✅ рекомендую — конфиг спавна структур без датапаков).

---

## 🌊 5. Океан (для подлодок Deep Seas)

| Мод | Обновлён | Что это | Вердикт |
|---|---|---|---|
| **Aquamirae** | 2026-10 | Кладбище кораблей, глубоководные твари, **босс** | ✅ Сразу и цель, и босс |
| Underwater Village | 2026-09 | Подводные структуры | ✅ |
| Unnamed Sea | 2026-02 | Структуры моря и пляжа | 🤔 |
| YUNG's Better Ocean Monuments | 2026-09 | Переделка монументов | ✅ (в наборе YUNG's) |

---

## 🔥 6. Незер

| Мод | Обновлён | Что это | Вердикт |
|---|---|---|---|
| **Incendium** | 2024-12 ⚠ | Переделка биомов Незера + сложные структуры, уникальный лут | ✅ Классика. Обновлялся давно — проверить на 1.21.1 |
| YUNG's Better Nether Fortresses | 2026-09 | Переделка крепостей | ✅ |
| Formations Nether | 2026-09 | Много структур | ✅ |
| Moog's Nether Structures | 2026-09 | Структуры из ванильных блоков | 🤔 Пересекается |
| Eternal Nether | 2026-09 | Структуры, мобы | 🤔 |
| Luminous Nether | 2026-09 | Биомы, опасные мобы | 🤔 Конфликт с Incendium по биомам |

---

## 🌌 7. Энд

| Мод | Обновлён | Что это | Вердикт |
|---|---|---|---|
| **Nullscape** | 2025-10 | Инопланетный, сюрреалистичный рельеф Энда | ✅ Отлично для полётов — гигантские формы |
| Stellarity (lite) | 2026-03 | 23 биома Энда, только генерация | 🤔 Альтернатива Nullscape |
| Enderscape | 2025-08 | 4 атмосферных биома, новые мобы | 🤔 Альтернатива |
| YUNG's Better End Island | 2026-09 | Переделка арены дракона | ✅ |
| Moog's End Structures | 2026-09 | Структуры в Энде | ✅ |
| The Lost Castle | 2026-05 | Огромный замок | 🤔 |
| Medieval Buildings End Edition | 2026-06 | Структуры Энда | 🤔 |

**Совет:** выбрать один генератор Энда (Nullscape), структуры — сверху.

---

## 🌀 8. Новые измерения

На 1–2 недели много измерений не освоить. Предлагаю **2–3 + космос**, каждое со своей причиной туда лететь.

| Мод | Скачиваний | Обновлён | Что это | Вердикт |
|---|---|---|---|---|
| **The Aether + Deep Aether** | 10,3 / 3,5 млн | 2025-09 / 2026-03 | Небесное измерение из островов, данжи с боссами | ✅ **Идеально под корабли** — летающие острова. Проверить, переходят ли корабли через портал (Dimensional Sable) |
| 🟠 **Twilight Forest** | — | — | Классическое измерение с цепочкой боссов-прогрессии | ✅ **Установлен** (4.8.3345, с CurseForge) |
| **Deeper and Darker (Otherside)** | 16 млн | 2026-06 | Тёмное измерение за Deep Dark | ✅ |
| Eternal Starlight | 0,75 млн | 2026-09 | Магическое «звёздное» измерение | 🤔 |
| The Undergarden | — | 2026-04 | Подземное измерение | 🤔 |
| The Bumblezone | 4,3 млн | 2026-10 | Измерение пчёл | 🤔 Весёлое, но не про технику |
| The Afterdark | 0,27 млн | 2026-08 | Альтернатива Deep Dark | ❌ Пересекается с Deeper and Darker |
| The Sift | 0,3 млн | 2026-10 | Странное измерение с ресурсами | 🤔 |
| Create: Dimension, Steamworks Realm | 0,03 млн | 2026-08 | Шахтёрское измерение для автоматизации | ❌ **Упрощает:** богатые жилы руды в одном месте |
| Dimensional Dungeons | 1,2 млн | 2026-01 | Бесконечные возобновляемые данжи | ❌ **Упрощает:** фарм лута без путешествий |

---

## 🧭 9. Навигация и атмосфера

| Мод | Вердикт |
|---|---|
| Traveler's Titles — названия биомов/измерений на экране | ✅ Атмосфера, ничего не упрощает |
| Structurify — настройка частоты структур | ✅ Инструмент баланса |
| Explorer's Compass / Nature's Compass — указывают на любую структуру/биом | ❌ **Упрощают:** убивают поиск. Вариант: только очень дорогой рецепт |
| Xaero's Minimap / World Map | 🤔 Удобно, но с картой поиск проще. Можно без отображения структур |
| Waystones, Dimensional Doors, Ender Chests/Tanks | ❌ Телепорты и общие инвентари |

---

## 🗡 10. Структуры с боссами — обсудим на этапе боссов

L_Ender's Cataclysm (+ Integrated Cataclysm), Aquamirae, The Graveyard, Bloxy's Structures, Born in Chaos, Myths and Legends, боссы Aether и Twilight Forest.

---

## Мой стартовый набор (на обсуждение)

1. **Космос:** Cosmonautics + Deep Air.
2. **Рельеф:** Tectonic + Terralith + Lithostitched.
3. **Пещеры:** YUNG's Cave Biomes, Galosphere, Deeper and Darker.
4. **Структуры:** YUNG's набор, Dungeons and Taverns, When Dungeons Arise, Towns and Towers, Moog's Voyager + Soaring, Sky Whale Ship, Structory, Structurify.
5. **Океан:** Aquamirae, Underwater Village.
6. **Незер:** Incendium (если работает) + YUNG's Fortresses + Formations Nether.
7. **Энд:** Nullscape + YUNG's End Island + Moog's End.
8. **Измерения:** Aether + Deep Aether, Twilight Forest (🟠 CurseForge), Otherside (из Deeper and Darker).
9. **Атмосфера:** Traveler's Titles.
