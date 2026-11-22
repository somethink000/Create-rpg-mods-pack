# Предметы модов: что оставить, что убрать, как крафтить

Начато 2026-11-17. Этап 4 плана ([roadmap.md](roadmap.md)).

## Правила
1. **Предмет либо особенный, либо его нет.** Оружие, броня и инструменты, которые отличаются только цифрами урона/защиты (условный «мега-меч, как алмазный»), удаляются: рецепты убираются, из лута и дропа вычищаются, из JEI скрываются.
2. **Особенные предметы делаются через Create.** Основа — материал с босса или из данжа маршрута (его нужно немного), остальное — детали Create своего тира: листы, механизмы, пластины, сталь TFMG и т. п. Сборка — на Mechanical Crafter или Sequenced Assembly, а не на верстаке.
3. **Материалы с боссов — «ядра» прогрессии.** Босс маршрута даёт материал → из него и деталей Create собирается снаряжение или деталь для следующего тира.
4. **Гринд без смысла убираем.** Если предмет нужно фармить десятками без связи с прогрессией — заменяем на путь через данжи маршрута и Create.
5. Решение по каждому моду — после согласования таблицы.

## Как определяем «особенный»
По коду jar (`javap`): у класса предмета переопределены игровые методы — `use`, `hurtEnemy`, `inventoryTick`, `releaseUsing`, `onUseTick` и т. п. — значит, у него своя механика. Класс без них или только с `getDefaultAttributeModifiers` — просто другие цифры.
Ограничения: способности брони иногда сделаны в обработчиках событий, а не в классе предмета (у Legendary Monsters есть клавиша «способность шлема») — такие предметы проверяются отдельно. Обычные мечи/инструменты из материалов модов (железное дерево Twilight, зейнит Aether…) сделаны на стандартных классах и в автоматический подсчёт не попадают.

## Сводка по модам (классы снаряжения)

| Мод | Классов | Без своей механики |
|---|---|---|
| Twilight Forest | 27 | 20 |
| The Aether | 34 | 19 |
| Bosses'Rise | 12 | 8 |
| Deep Aether | 17 | 8 |
| Mowzie's Mobs | 12 | 6 |
| Cataclysm | 11 | 3 |
| Galosphere, Aquamirae, Apotheosis, Deeper and Darker | 9 | 7 |
| Relics, Artifacts, BoMD, Qliphoth, Incendium | — | все с механикой или своя система |

## Пилот: Legendary Monsters (на согласование)

| Решение | Предметы |
|---|---|
| **Убрать** (только цифры) | Knight's Sword, Shattered Greatsword, Void Sword, Golden Halberd, Somber Key (меч), Anchor Handle, инструменты Enderitium (меч, топор, кирка, лопата, мотыга, серп), щиты Spiky / Frostbitten / Shulker; Atmospheric Boots и Withered Ribcage — проверить, нет ли способности в событиях |
| **Оставить, крафт через Create** (есть механика) | Axe of Lightning, Chorus Blade, Chorus Cannon, Hand Cannon, Monstrous Anchor, Soul Greatsword, The Great Frost, The Tesseract, Withered Scythe, Resurrected Javelin, Fiery Jaw, Dinosaur Bone Club, Mossy Hammer, Atom Splitter, Fiery Boots, Mossy Chestplate, броня Annihilator, Buckler of Annihilation |
| **Проверить на личный полёт** | Wand of Clouds |
| **Шлемы — проверить** (способность через клавишу) | Blast-proof, Chorus, Shulker, Dinosaur Bone |
| **Материалы-ядра** (основа крафтов) | Lava Eater's Skin, Corrupted Soul, Molten Metal Ingot, Ancient Sandstone Shard, Somber Key Part, Living Stone |
| **Призыв боссов** (оставить, вписать в маршрут) | «Глаза» боссов: Eye of Moss / Frost / Magma / Bones / Sandstorm / Shulker / Chorus / Soul / Ghost / Air / Many Ribs / Annihilation |
