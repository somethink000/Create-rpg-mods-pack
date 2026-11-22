# Модуль 2: бижутерия, реликвии, артефакты

Сгенерировано из jar 2026-11-17. Своя механика есть у всех — вопрос в источнике.

## Предложение

1. **Artifacts (47):** сейчас все вкладываются глобальными модификаторами лута в сундуки всего мира. Отключить эти модификаторы (данными в моде-ядре). Артефакты остаются трофеями боссов по таблице из [loot-artifacts.md](loot-artifacts.md) и/или крафтятся через Create из деталей босса.
2. **Relics (21):** раздаются собственной системой мода (`config/relics.yaml`). Отключить появление в сундуках, закрепить за боссами маршрута.
3. **Aether, Deep Aether:** аксессуары лежат в наградах своих данжей (бронзовый, серебряный, золотой, латунный) — это боковая ветка маршрута, оставляем; кольца и подвески, которые крафтятся, — перевести на Create.
4. **Cataclysm:** сундуки своих данжей и дроп боссов (данжи маршрута) — оставляем; «глаза» для поиска данжей крафтятся — встроить в маршрут (книга сама ведёт к данжу, глаза можно убрать или сделать подсказкой).
5. **Полёт и телепорт** (Warp Drive, Chorus Totem, Chorus Staff, Cloud in a Bottle, Bunny Hoppers, Aqua-Dashers, Strider Shoes, Springy Boot, Roller Skate, Floaty Scarf) — проверить на тестовом сервере, не обходят ли они путешествия на кораблях.

## Сейчас: откуда что берётся

### artifacts (47)
- Angler's Hat (`anglers_hat`) — вкладывается в чужие сундуки
- Antidote Vessel (`antidote_vessel`) — вкладывается в чужие сундуки
- Aqua-Dashers (`aqua_dashers`) — вкладывается в чужие сундуки
- Bunny Hoppers (`bunny_hoppers`) — вкладывается в чужие сундуки
- Charm of Shrinking (`charm_of_shrinking`) — вкладывается в чужие сундуки
- Charm of Sinking (`charm_of_sinking`) — вкладывается в чужие сундуки
- Chorus Totem (`chorus_totem`) — вкладывается в чужие сундуки
- Cloud in a Bottle (`cloud_in_a_bottle`) — вкладывается в чужие сундуки
- Cowboy Hat (`cowboy_hat`) — вкладывается в чужие сундуки
- Cross Necklace (`cross_necklace`) — вкладывается в чужие сундуки
- Crystal Heart (`crystal_heart`) — вкладывается в чужие сундуки
- Digging Claws (`digging_claws`) — вкладывается в чужие сундуки
- Eternal Steak (`eternal_steak`) — крафт
- Everlasting Beef (`everlasting_beef`) — вкладывается в чужие сундуки
- Feral Claws (`feral_claws`) — вкладывается в чужие сундуки
- Fire Gauntlet (`fire_gauntlet`) — вкладывается в чужие сундуки
- Flame Pendant (`flame_pendant`) — вкладывается в чужие сундуки
- Flippers (`flippers`) — вкладывается в чужие сундуки
- Golden Hook (`golden_hook`) — вкладывается в чужие сундуки
- Helium Flamingo (`helium_flamingo`) — вкладывается в чужие сундуки
- Kitty Slippers (`kitty_slippers`) — вкладывается в чужие сундуки
- Lucky Scarf (`lucky_scarf`) — вкладывается в чужие сундуки
- Night Vision Goggles (`night_vision_goggles`) — вкладывается в чужие сундуки
- Novelty Drinking Hat (`novelty_drinking_hat`) — вкладывается в чужие сундуки
- Obsidian Skull (`obsidian_skull`) — вкладывается в чужие сундуки
- Onion Ring (`onion_ring`) — вкладывается в чужие сундуки
- Panic Necklace (`panic_necklace`) — вкладывается в чужие сундуки
- Pickaxe Heater (`pickaxe_heater`) — вкладывается в чужие сундуки
- Plastic Drinking Hat (`plastic_drinking_hat`) — вкладывается в чужие сундуки
- Pocket Piston (`pocket_piston`) — вкладывается в чужие сундуки
- Power Glove (`power_glove`) — вкладывается в чужие сундуки
- Rooted Boots (`rooted_boots`) — вкладывается в чужие сундуки
- Running Shoes (`running_shoes`) — вкладывается в чужие сундуки
- Scarf of Invisibility (`scarf_of_invisibility`) — вкладывается в чужие сундуки
- Shock Pendant (`shock_pendant`) — вкладывается в чужие сундуки
- Snorkel (`snorkel`) — вкладывается в чужие сундуки
- Snowshoes (`snowshoes`) — вкладывается в чужие сундуки
- Steadfast Spikes (`steadfast_spikes`) — вкладывается в чужие сундуки
- Strider Shoes (`strider_shoes`) — вкладывается в чужие сундуки
- Superstitious Hat (`superstitious_hat`) — вкладывается в чужие сундуки
- Thorn Pendant (`thorn_pendant`) — вкладывается в чужие сундуки
- Universal Attractor (`universal_attractor`) — вкладывается в чужие сундуки
- Vampiric Glove (`vampiric_glove`) — вкладывается в чужие сундуки
- Villager Hat (`villager_hat`) — вкладывается в чужие сундуки
- Warp Drive (`warp_drive`) — вкладывается в чужие сундуки
- Whoopee Cushion (`whoopee_cushion`) — вкладывается в чужие сундуки
- Withered Bracelet (`withered_bracelet`) — вкладывается в чужие сундуки

### relics (21)
- Chef's Hat (`chef_hat`) — своя система мода
- Chorus Staff (`chorus_staff`) — своя система мода
- Clot of Time (`clot_of_time`) — своя система мода
- Cut Glass Boot (`cut_glass_boot`) — своя система мода
- Experience Disperser (`experience_disperser`) — своя система мода
- Ghostly Mantle (`ghostly_mantle`) — своя система мода
- Glitchy Mantle (`glitchy_mantle`) — своя система мода
- Hunting Belt (`hunting_belt`) — своя система мода
- Jellyfish Necklace (`jellyfish_necklace`) — своя система мода
- Kinetic Belt (`kinetic_belt`) — своя система мода
- Leafy Mantle (`leafy_mantle`) — своя система мода
- Midnight Mantle (`midnight_mantle`) — своя система мода
- Piglin Mask (`piglin_mask`) — своя система мода
- Reflective Necklace (`reflective_necklace`) — своя система мода
- Relic Experience Bottle (`relic_experience_bottle`) — своя система мода
- Rider Flute (`rider_flute`) — своя система мода
- Ring of the Seven Deadly Sins (`ring_of_the_seven_deadly_sins`) — своя система мода
- Roller Skate (`roller_skate`) — своя система мода
- Shield of Retaliation (`shield_of_retaliation`) — своя система мода
- Sphere of Self-Sacrifice (`sphere_of_self_sacrifice`) — своя система мода
- Springy Boot (`springy_boot`) — своя система мода

### cataclysm (19)
- Belt of Beginner (`belt_of_beginner`) — крафт
- Berserker Soul Amulet (`berserker_soul_amulet`) — сундуки: abandoned_treasure
- Blazing Grips (`blazing_grips`) — крафт
- Chitin Claw (`chitin_claw`) — мобы: clawdian
- Eye of Abyss (`abyss_eye`) — крафт
- Eye of Curse (`cursed_eye`) — крафт
- Eye of Desert (`desert_eye`) — крафт
- Eye of Flame (`flame_eye`) — крафт
- Eye of Mech (`mech_eye`) — крафт
- Eye of Monstrous (`monstrous_eye`) — крафт
- Eye of Storm (`storm_eye`) — крафт
- Eye of Void (`void_eye`) — крафт
- Monstrous Horn (`monstrous_horn`) — мобы: netherite_monstrosity
- Necklace of the desert (`necklace_of_the_desert`) — сундуки: cursed_pyramid, cursed_pyramid_necklace
- Ring of Grudged (`ring_of_grudged`) — сундуки: abandoned_treasure
- Sticky Gloves (`sticky_gloves`) — крафт
- Sturdy Boots (`sturdy_boots`) — сундуки: abandoned, abandoned_treasure
- Unbreakable Skull (`unbreakable_skull`) — сундуки: desert_treasure
- Vitality Ankh (`vitality_ankh`) — сундуки: desert_treasure

### aether (17)
- Agility Cape (`agility_cape`) — сундуки: bronze_dungeon_reward
- Blue Cape (`blue_cape`) — крафт
- Golden Pendant (`golden_pendant`) — сундуки: silver_dungeon_loot; крафт
- Golden Ring (`golden_ring`) — сундуки: bronze_dungeon_loot, silver_dungeon_loot; крафт
- Ice Pendant (`ice_pendant`) — сундуки: bronze_dungeon_loot, gold_dungeon_reward; крафт
- Ice Ring (`ice_ring`) — сундуки: gold_dungeon_reward; крафт
- Invisibility Cloak (`invisibility_cloak`) — сундуки: silver_dungeon_reward, silver_dungeon_treasure
- Iron Pendant (`iron_pendant`) — сундуки: silver_dungeon_loot; крафт
- Iron Ring (`iron_ring`) — сундуки: bronze_dungeon_loot, brass_dungeon_loot; мобы: mimic; крафт
- Red Cape (`red_cape`) — крафт
- Swet Cape (`swet_cape`) — сундуки: bronze_dungeon_loot
- Valkyrie Cape (`valkyrie_cape`) — сундуки: silver_dungeon_cape_config
- Valkyrie Gloves (`valkyrie_gloves`) — сундуки: silver_dungeon_valkyrie
- White Cape (`white_cape`) — крафт
- Yellow Cape (`yellow_cape`) — крафт
- Zanite Pendant (`zanite_pendant`) — сундуки: bronze_dungeon_reward, silver_dungeon_loot; крафт
- Zanite Ring (`zanite_ring`) — сундуки: bronze_dungeon_reward, silver_dungeon_loot; крафт

### deep_aether (9)
- Aercloud Necklace (`aercloud_necklace`) — сундуки: brass_dungeon_treasure
- Cloud Cape (`cloud_cape`) — сундуки: brass_dungeon_reward, brass_dungeon_treasure
- Eye Of The Slider (`slider_eye`) — своя система мода
- Floaty Scarf (`floaty_scarf`) — своя система мода
- Gravitite Ring (`gravitite_ring`) — крафт
- Skyjade Pendant (`skyjade_pendant`) — своя система мода
- Skyjade Ring (`skyjade_ring`) — сундуки: brass_dungeon_reward; крафт
- Spooky Ring (`spooky_ring`) — своя система мода
- Stratus Ring (`stratus_ring`) — крафт

### legendary_monsters (9)
- Bottle Of Annihilation (`bottle_of_annihilation`) — сундуки: space_station_loot_table_basic, space_station_loot_table_luxury; мобы: flame_drifter, wandering_eye; крафт
- Enderitium Gem (`enderitium_gem`) — сундуки: space_station_loot_table_basic, space_station_loot_table_luxury; мобы: annihilation_pursuer, flame_drifter; крафт; вкладывается в чужие сундуки
- Somber Key Ring (`somber_key_ring`) — мобы: resurrected_knight
- Totem Of Moss (`totem_of_moss`) — мобы: mossy_golem; крафт
- Void Gem (`void_gem`) — своя система мода
- Withered Horn (`withered_horn`) — своя система мода
- §6Right - Click on a Block to summon a Mossy Golem that makes crops grow way faster while near ungrown crops  (`totem_of_moss1`) — своя система мода
- §7and can help you during fights. Golem is automatically tamed. It can be respawned easily after death  (`totem_of_moss2`) — своя система мода
- §fShift + Right Click on golem to enable '§aStand Still Mode§7' (`totem_of_moss3`) — своя система мода

### twilightforest (9)
- Charm of Keeping I (`charm_of_keeping_1`) — сундуки: basement, tower_foyer; мобы: maze_slime; вкладывается в чужие сундуки
- Charm of Keeping II (`charm_of_keeping_2`) — крафт; вкладывается в чужие сундуки
- Charm of Keeping III (`charm_of_keeping_3`) — крафт
- Charm of Life I (`charm_of_life_1`) — сундуки: tower_room; вкладывается в чужие сундуки
- Charm of Life II (`charm_of_life_2`) — крафт
- Crumble Horn (`crumble_horn`) — сундуки: questing_ram_rewards
- Knightmetal Loop (`knightmetal_ring`) — крафт
- Rabbit's Pocket Watch (`pocket_watch`) — вкладывается в чужие сундуки
- Talisman of the Cube (`cube_talisman`) — своя система мода

### createbigcannons (5)
- Gas Mask (`gas_mask`) — крафт
- Partial Recoil Spring (`partial_recoil_spring`) — своя система мода
- Quick-Firing Mechanism (`quickfiring_mechanism`) — крафт
- Recoil Spring (`recoil_spring`) — крафт
- Spring Wire (`spring_wire`) — крафт

### createfood (5)
- Meringue (`meringue_bowl`) — крафт
- Meringue Bucket (`meringue_bucket`) — своя система мода
- Meringue Cookie (`meringue_cookie`) — крафт; вкладывается в чужие сундуки
- Onion Rings (`onion_rings`) — крафт
- Raw Onion Rings (`raw_onion_rings`) — крафт

### dnt (4)
- Lingering Potion of Blindness (`blindness_lingering_potion`) — своя система мода
- Lingering Potion of Decay (`wither_lingering_potion`) — своя система мода
- Lingering Potion of Dizziness (`nausea_lingering_potion`) — своя система мода
- Lingering Potion of Levitation (`levitation_lingering_potion`) — своя система мода

### create (3)
- Engineer's Goggles (`goggles`) — сундуки: rare_chest; крафт; вкладывается в чужие сундуки
- Mechanical Belt (`belt_connector`) — сундуки: loot_belts; крафт; вкладывается в чужие сундуки
- Transmitter (`transmitter`) — крафт

### advancedperipherals (2)
- Netherite reinforced Smart Glasses (`smart_glasses_netherite`) — крафт
- Smart Glasses (`smart_glasses`) — крафт

### apotheosis (2)
- Charm of %s (`potion_charm`) — своя система мода
- Invalid Potion Charm (`potion_charm_broke`) — своя система мода

### bosses_of_mass_destruction (2)
- Blazing Eye (`blazing_eye`) — сундуки: gauntlet
- Void Thorn (`void_thorn`) — мобы: void_blossom

### computercraft (2)
- Advanced Pocket Computer (`pocket_computer_advanced`) — крафт
- Pocket Computer (`pocket_computer_normal`) — крафт

### garnished (2)
- Gloomy Gathering (`gloomy_gathering`) — крафт
- Thorn on a Stick (`thorn_on_a_stick`) — крафт

### pipeorgans (2)
- Incomplete English Horn (`incomplete_english_horn`) — своя система мода
- Incomplete Krummhorn (`incomplete_krummhorn`) — своя система мода

### create_dragons_plus (1)
- Dyenamics Spring Green Dye Bucket (`dyenamics_spring_green_dye_bucket`) — своя система мода

### dndecor (1)
- Mechanical Belt (Full) (`belt_connector`) — крафт; вкладывается в чужие сундуки

### galosphere (1)
- Bottle Of Spectre (`bottle_of_spectre`) — своя система мода

### create_bic_bit (1)
- Bucket of Herring (`herring_bucket`) — своя система мода

### create_connected (1)
- Linked Transmitter (`linked_transmitter`) — крафт

### extra_gauges (1)
- String Gauge (`string_gauge`) — крафт
