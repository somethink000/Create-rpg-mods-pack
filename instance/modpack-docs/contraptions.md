# Контрапции Create и замены из Aeronautics

Дата: 2026-11-06. Блоки взяты из lang-файлов установленных модов.
Цель: убрать дешёвую автоматизацию на движущихся конструкциях; движение переносим на физику Sable (Aeronautics / Simulated / Offroad), где оно дороже и сложнее в постройке.

## ✅ Уже сделано

- **Контрапции на вагонетках запрещены** — `kubejs/server_scripts/ban_minecart_contraptions.js`:
  удалены рецепты Cart Assembler и Minecart Coupling, установка Cart Assembler отменяется (на случай лута), предметы скрыты из JEI/EMI (`kubejs/client_scripts/hide_banned_items.js`).
  Проверка по всем jar: собрать вагонеточную контрапцию можно только через Cart Assembler из Create.
  ⚠ Не покрыто: Cart Assembler, уже стоящий в сгенерированной структуре, будет работать. Если найдём такие — уберём через LootJS/структуры.

## 1. Что двигает конструкцию (сборщики)

| Блок Create | Что делает | Замена в Aeronautics-семье | Комментарий |
|---|---|---|---|
| Cart Assembler + Minecart Coupling | Контрапция на вагонетке | **Offroad**: Wheel Mount + Tire/Large/Monstrous Tire + Portable Engine (Simulated) — физическая машина | ❌ **Запрещено** |
| Mechanical Piston / Sticky Piston / Piston Extension Pole | Линейное движение | **Pneumatic Rod / Giant Hydraulic Rod / Pneumatic Regulator** (Transmission & Linkage), **Spring** (Simulated) | Физические поршни между частями Sable |
| Gantry Carriage + Gantry Shaft | Движение по рельсу-валу | Pneumatic Rod; Rope Winch | Прямой замены «по рельсу» нет |
| Mechanical Bearing | Вращение конструкции | **Swivel Bearing** (Simulated), **Stabilizer Bearing** (Stabilized), Kinetic Conversion Bearing | Swivel Bearing — шарнир между физ. объектами |
| Windmill Bearing + Sail | Ветряк → SU | — (на кораблях: Propeller Bearing работает наоборот, тратит SU) | Оставить: это генерация энергии, не автоматизация |
| Clockwork Bearing | Стрелки часов | Swivel Bearing | Декор, можно оставить |
| Rope Pulley / Elevator Pulley | Подъём вниз/вверх, лифты | **Rope Winch + Rope Connector/Coupling** (Simulated), **Pulley + Steel Cable** (Deep Seas) | Подъём физ. объектов на тросе |
| Linear / Secondary / Radial Chassis | Склейка больших контрапций | **Physics Assembler + Honey Glue / Merging Glue** (Simulated) | |
| Super Glue | Склейка | Honey Glue | |
| Sticker | Временная склейка | Docking Connector / Paired Docking Connector, Redstone Magnet (Simulated) | Стыковка физ. объектов |
| Train (Station, Controls, Bogey) | Поезда | Поезда Create; Simurail Station (Automated Logistics, экспериментально) | Поезда оставить — это транспорт |

## 2. Что работает на движущейся конструкции (актёры)

| Блок Create | На контрапции делает | Замена в Aeronautics-семье | Риск упрощения |
|---|---|---|---|
| Mechanical Drill | Копает туннели/карьеры | **Offroad Borehead Bearing + Rock Cutting Wheel** (буровая машина Sable, собирает блоки в хранилище); **Miner Bearing** (Create: Rock & Stone, умеет работать на физ. объектах) | 🔴 Высокий |
| Mechanical Saw | Рубит деревья | — | 🟠 Средний |
| Mechanical Harvester | Собирает урожай | — | 🟠 Средний |
| Mechanical Plough | Ломает/вспахивает | — | 🟠 Средний |
| Mechanical Roller | Укладывает дороги/пути | — | 🟠 Средний |
| Deployer (на контрапции) | Ставит блоки/использует предметы на ходу | — | 🟠 Средний |
| Portable Storage / Fluid Interface | Обмен предметами с контрапцией | **Docking Connector** + **Airship Station** (Automated Logistics) | 🟢 Низкий |
| Seat | Сиденье | Seat / Interiors / Steering Wheel | 🟢 |
| Contraption Controls | Вкл/выкл актёров на ходу | **Aeroworks**: Control Desk/Stand, модули кнопок/рычагов/джойстиков; Throttle Lever, Steering Wheel (Simulated) | 🟢 |

⚠ **Надо проверить в игре:** работают ли актёры Create (бур, комбайн) на кораблях Sable. Если да — запрет «на контрапциях» через тег `create:non_movable` может не действовать на корабли, или наоборот сломать их сборку.

## 3. Найдено по ходу — нужно твоё решение

| Что | Мод | Проблема |
|---|---|---|
| **Copper Leg Thrusters** | Cosmonautics | 🔴 Дают **творческий полёт** на сжатом воздухе. Рецепт дешёвый: андезит, медь, ламинария, медные сопла. Обесценивает корабли |
| **Cosmonautics Jetpack** | Cosmonautics | 🟠 Джетпак на топливе. Рецепт поздний: незеритовый бактанк + титан + механизм |
| Copper Anchor Boots | Cosmonautics | 🟢 Гравитация в космосе — нормально |
| Contraption Cloner / Mover / Remover | Create Propulsion | 🟢 Клонирование контрапций — **рецептов нет**, только креатив |
| Auto Assembly, Auto Glue | Create Propulsion | 🟢 Удобство сборки, без рецептов (креатив) |
| Coral Generator, Ion Thruster, кабели FE | Create Propulsion | ⚡ **Ещё один источник FE** (генератор на коралловой жидкости) — учесть в решении по FE |
| Borehead Bearing + Rock Cutting Wheel | Offroad (часть Aeronautics) | 🔴 Буровая машина — если запрещаем буры на контрапциях, решить и про неё |

## 4. Варианты политики (на обсуждение)

1. **Минимум:** только вагонетки (сделано) + дорогие рецепты актёров.
2. **Средний (мой совет):** вагонетки + тег `non_movable` на Drill/Saw/Harvester/Plough/Roller (стационарно работают, на движущихся — нет) + лимит размера контрапций в конфиге Create. Копка на ходу — только Borehead Bearing (дорогой рецепт) или Miner Bearing Rock & Stone с конечными залежами.
3. **Жёсткий:** как 2 + запрет поршней/портальных кранов (замена — пневмоштоки Transmission & Linkage).
