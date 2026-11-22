# Модуль 1: оружие, броня, щиты, инструменты — разметка

Сгенерировано из jar 2026-11-17, на согласование.

Как размечено:
1. Класс каждого предмета — из байткода регистрации мода; проверено, переопределяет ли класс (или его родитель в том же моде) игровые методы (`use`, `hurtEnemy`, `inventoryTick`, `releaseUsing`, `canElytraFly`…).
2. Для остальных — есть ли обработчики событий мода, которые ссылаются на предмет (бонусы сета, эффекты при ударе/уроне).
3. Оставшиеся — вручную по механике модов (способности, унаследованные от общих классов: инструменты Aether, броня Twilight, сеты Aquamirae).

- **Особенный** → оставить, крафт через Create из материала босса + деталей Create.
- **Цифры** → убрать (рецепты, лут, дроп, JEI).
- **Проверить** → проверю в игре на тестовом сервере.
Исключены: косметика, еда, кадры анимации, полуфабрикаты сборки, «ставящиеся» копии предметов, книги, запрещённое снаряжение полёта (уже убрано в `ban_player_flight.js`).

Всего 592: особенных 420, цифры 152, проверить 20.

| Мод | Особенные | Цифры | Проверить |
|---|---|---|---|
| aether | 43 | 33 | 0 |
| twilightforest | 40 | 30 | 4 |
| legendary_monsters | 27 | 19 | 4 |
| cataclysm | 32 | 5 | 2 |
| deep_aether | 23 | 11 | 1 |
| createqol | 27 | 0 | 0 |
| cgs | 27 | 0 | 0 |
| aquamirae | 17 | 9 | 0 |
| deeperdarker | 7 | 10 | 4 |
| tfmg | 7 | 14 | 0 |
| creategoggles | 20 | 0 | 0 |
| block_factorys_bosses | 13 | 1 | 5 |
| garnished | 18 | 0 | 0 |
| mowziesmobs | 18 | 0 | 0 |
| create | 13 | 1 | 0 |
| apothic_enchanting | 12 | 1 | 0 |
| createbigcannons | 4 | 8 | 0 |
| create_things_and_misc | 11 | 0 | 0 |
| galosphere | 7 | 0 | 0 |
| graveyard | 6 | 0 | 0 |
| farmersdelight | 5 | 0 | 0 |
| create_confectionery | 5 | 0 | 0 |
| extendedwrenches | 0 | 5 | 0 |
| create_deep_dark | 5 | 0 | 0 |
| create_the_air_wars | 1 | 4 | 0 |
| aeronautics_utility_objects | 4 | 0 | 0 |
| create_submarine | 4 | 0 | 0 |
| createnuclear | 4 | 0 | 0 |
| cbcmoreshells | 2 | 1 | 0 |
| dndesires | 3 | 0 | 0 |
| create_ltab | 3 | 0 | 0 |
| rocketnautics | 2 | 0 | 0 |
| dnt | 2 | 0 | 0 |
| advancedperipherals | 1 | 0 | 0 |
| bosses_of_mass_destruction | 1 | 0 | 0 |
| createcasing | 1 | 0 | 0 |
| artifacts | 1 | 0 | 0 |
| computercraft | 1 | 0 | 0 |
| ntgl | 1 | 0 | 0 |
| shaolib | 1 | 0 | 0 |
| shaolib_munitions | 1 | 0 | 0 |

## aether
**Особенные → крафт через Create:** Candy Cane Sword (`candy_cane_sword`), Cloud Staff (`cloud_staff`), Enchanted Dart Shooter (`enchanted_dart_shooter`), Flaming Sword (`flaming_sword`), Golden Dart Shooter (`golden_dart_shooter`), Gravitite Axe (`gravitite_axe`), Gravitite Gloves (`gravitite_gloves`), Gravitite Hoe (`gravitite_hoe`), Gravitite Pickaxe (`gravitite_pickaxe`), Gravitite Shovel (`gravitite_shovel`), Gravitite Sword (`gravitite_sword`), Hammer of Kingbdogz (`hammer_of_kingbdogz`), Holy Sword (`holy_sword`), Holystone Axe (`holystone_axe`), Holystone Hoe (`holystone_hoe`), Holystone Pickaxe (`holystone_pickaxe`), Holystone Shovel (`holystone_shovel`), Holystone Sword (`holystone_sword`), Lightning Knife (`lightning_knife`), Lightning Sword (`lightning_sword`), Neptune Gloves (`neptune_gloves`), Obsidian Gloves (`obsidian_gloves`), Phoenix Bow (`phoenix_bow`), Phoenix Gloves (`phoenix_gloves`), Pig Slayer (`pig_slayer`), Poison Dart Shooter (`poison_dart_shooter`), Shield of Repulsion (`shield_of_repulsion`), Skyroot Axe (`skyroot_axe`), Skyroot Hoe (`skyroot_hoe`), Skyroot Pickaxe (`skyroot_pickaxe`), Skyroot Shovel (`skyroot_shovel`), Skyroot Sword (`skyroot_sword`), Valkyrie Axe (`valkyrie_axe`), Valkyrie Hoe (`valkyrie_hoe`), Valkyrie Lance (`valkyrie_lance`), Valkyrie Pickaxe (`valkyrie_pickaxe`), Valkyrie Shovel (`valkyrie_shovel`), Vampire Blade (`vampire_blade`), Zanite Axe (`zanite_axe`), Zanite Hoe (`zanite_hoe`), Zanite Pickaxe (`zanite_pickaxe`), Zanite Shovel (`zanite_shovel`), Zanite Sword (`zanite_sword`)

**Цифры → убрать:** Chainmail Gloves (`chainmail_gloves`), Diamond Gloves (`diamond_gloves`), Golden Gloves (`golden_gloves`), Gravitite Boots (`gravitite_boots`), Gravitite Chestplate (`gravitite_chestplate`), Gravitite Helmet (`gravitite_helmet`), Gravitite Leggings (`gravitite_leggings`), Iron Gloves (`iron_gloves`), Leather Gloves (`leather_gloves`), Nature Staff (`nature_staff`), Neptune Boots (`neptune_boots`), Neptune Chestplate (`neptune_chestplate`), Neptune Helmet (`neptune_helmet`), Neptune Leggings (`neptune_leggings`), Netherite Gloves (`netherite_gloves`), Obsidian Boots (`obsidian_boots`), Obsidian Chestplate (`obsidian_chestplate`), Obsidian Helmet (`obsidian_helmet`), Obsidian Leggings (`obsidian_leggings`), Phoenix Boots (`phoenix_boots`), Phoenix Chestplate (`phoenix_chestplate`), Phoenix Helmet (`phoenix_helmet`), Phoenix Leggings (`phoenix_leggings`), Sentry Boots (`sentry_boots`), Valkyrie Boots (`valkyrie_boots`), Valkyrie Chestplate (`valkyrie_chestplate`), Valkyrie Helmet (`valkyrie_helmet`), Valkyrie Leggings (`valkyrie_leggings`), Zanite Boots (`zanite_boots`), Zanite Chestplate (`zanite_chestplate`), Zanite Gloves (`zanite_gloves`), Zanite Helmet (`zanite_helmet`), Zanite Leggings (`zanite_leggings`)

## twilightforest
**Особенные → крафт через Create:** Blank Magic Map (`magic_map`), Blank Maze Map (`maze_map`), Blank Maze/Ore Map (`ore_map`), Block and Chain (`block_and_chain`), Diamond Minotaur Axe (`diamond_minotaur_axe`), Ender Bow (`ender_bow`), Fiery Boots (`fiery_boots`), Fiery Chestplate (`fiery_chestplate`), Fiery Helmet (`fiery_helmet`), Fiery Leggings (`fiery_leggings`), Fiery Pickaxe (`fiery_pickaxe`), Fiery Sword (`fiery_sword`), Giant's Pickaxe (`giant_pickaxe`), Giant's Sword (`giant_sword`), Glass Sword (`glass_sword`), Golden Minotaur Axe (`gold_minotaur_axe`), Ice Bow (`ice_bow`), Ice Sword (`ice_sword`), Knightmetal Axe (`knightmetal_axe`), Knightmetal Pickaxe (`knightmetal_pickaxe`), Knightmetal Sword (`knightmetal_sword`), Magic Map (`filled_magic_map`), Maze Map (`filled_maze_map`), Maze/Ore Map (`filled_ore_map`), Mazebreaker (`mazebreaker_pickaxe`), Mystic Crown (`mystic_crown`), Phantom Chestplate (`phantom_chestplate`), Phantom Helmet (`phantom_helmet`), Scepter of Fortification (`fortification_scepter`), Scepter of Life Draining (`lifedrain_scepter`), Scepter of Twilight (`twilight_scepter`), Seeker Bow (`seeker_bow`), Traveller's Goggles (`travellers_goggles`), Traveller's Wings (`travellers_wings`), Tri-Bow (`triple_bow`), Yeti Boots (`yeti_boots`), Yeti Horned Helm (`yeti_helmet`), Yeti Jacket (`yeti_chestplate`), Yeti Leggings (`yeti_leggings`), Zombie Scepter (`zombie_scepter`)

**Цифры → убрать:** Arctic Boots (`arctic_boots`), Arctic Hood (`arctic_helmet`), Arctic Jacket (`arctic_chestplate`), Arctic Leggings (`arctic_leggings`), Crown Splinter (`crown_splinter`), Ironwood Axe (`ironwood_axe`), Ironwood Boots (`ironwood_boots`), Ironwood Chestplate (`ironwood_chestplate`), Ironwood Helmet (`ironwood_helmet`), Ironwood Hoe (`ironwood_hoe`), Ironwood Leggings (`ironwood_leggings`), Ironwood Pickaxe (`ironwood_pickaxe`), Ironwood Shovel (`ironwood_shovel`), Ironwood Sword (`ironwood_sword`), Knightmetal Boots (`knightmetal_boots`), Knightmetal Chestplate (`knightmetal_chestplate`), Knightmetal Greaves (`knightmetal_leggings`), Knightmetal Helmet (`knightmetal_helmet`), Knightmetal Shield (`knightmetal_shield`), Naga Scale Leggings (`naga_leggings`), Naga Scale Tunic (`naga_chestplate`), Steeleaf Axe (`steeleaf_axe`), Steeleaf Boots (`steeleaf_boots`), Steeleaf Chestplate (`steeleaf_chestplate`), Steeleaf Helmet (`steeleaf_helmet`), Steeleaf Hoe (`steeleaf_hoe`), Steeleaf Leggings (`steeleaf_leggings`), Steeleaf Pickaxe (`steeleaf_pickaxe`), Steeleaf Shovel (`steeleaf_shovel`), Steeleaf Sword (`steeleaf_sword`)

**Проверить:** Traveller's Belt (`travellers_belt`), Traveller's Boots (`travellers_boots`), Traveller's Gloves (`travellers_gloves`), Traveller's Vest (`travellers_vest`)

## legendary_monsters
**Особенные → крафт через Create:** Annihilator's Boots (`annihilator_boots`), Annihilator's Chestplate (`annihilator_chestplate`), Annihilator's Helmet (`annihilator_helmet`), Annihilator's Leggings (`annihilator_leggings`), Atom Splitter (`atom_splitter`), Axe Of Lightning (`axe_of_lightning`), Buckler Of Annihilation (`buckler_of_annihilation`), Chorus Blade (`chorus_blade`), Chorus Cannon (`chorus_cannon`), Dinosaur Bone Club (`dinosaur_bone_club`), Fiery Boots (`fiery_boots`), Fiery Jaw (`fiery_jaw`), Frostbitten Shield (`frostbitten_shield`), Golden Halberd (`golden_halbert`), Monstrous Anchor (`monstrous_anchor`), Mossy Chestplate (`mossy_chestplate`), Mossy Hammer (`mossy_hammer`), Resurrected Javelin (`resurrected_javelin`), Sand Cannon (`sand_cannon`), Shulker Shield (`shulker_shield`), Soul Great Sword (`soul_great_sword`), Spiky Shield (`spiky_shield`), The Great Frost (`the_great_frost`), The Tesseract (`the_tesseract`), Wand Of Clouds (`wand_of_clouds`), Withered Ribcage (`withered_ribcage`), Withered Scythe (`withered_scythe`)

**Цифры → убрать:** Cloud Rod (`cloud_rod`), Dinosaur Bone Shield (`dinosaur_bone_shield`), Ender Hammer (`ender_hammer`), Enderitium Axe (`enderitium_axe`), Enderitium Hoe (`enderitium_hoe`), Enderitium Pickaxe (`enderitium_pickaxe`), Enderitium Shovel (`enderitium_shovel`), Enderitium Sickle (`enderitium_sickle`), Enderitium Sword (`enderitium_sword`), Knight's Sword (`knights_sword`), Knight's Sword Blade Half (`knights_sword_blade_half`), Reinforced Monstrous Anchor (`reinforced_monstrous_anchor`), Sharp Battle Axe (`sharp_battle_axe`), Shattered Greatsword (`shattered_greatsword`), Sickle Of Harvest (`sickle_of_harvest`), Somber Key (`somber_key`), Spiky Spear (`spiky_spear`), Void Sword (`void_sword`), §6Extremely Durable! (`dino_shield`)

**Проверить:** Atmospheric Boots (`atmospheric_boots`), Blastproof Helmet (`blastproof_helmet`), Chorus Mask (`chorus_mask`), Shulker Helmet (`shulker_helmet`)

## cataclysm
**Особенные → крафт через Create:** Ancient Spear (`ancient_spear`), Astrape (`astrape`), Athame (`athame`), Azure Sea Shield (`azure_sea_shield`), Bloom Stone Pauldrons (`bloom_stone_pauldrons`), Brontes (`brontes`), Ceraunus (`ceraunus`), Coral Bardiche (`coral_bardiche`), Coral Spear (`coral_spear`), Cursed Bow (`cursed_bow`), Cursium Boots (`cursium_boots`), Cursium Chestplate (`cursium_chestplate`), Cursium Helmet (`cursium_helmet`), Cursium Leggings (`cursium_leggings`), Gauntlet of Bulwark (`gauntlet_of_bulwark`), Gauntlet of Guard (`gauntlet_of_guard`), Gauntlet of Maelstrom (`gauntlet_of_maelstrom`), Ignitium Boots (`ignitium_boots`), Ignitium Chestplate (`ignitium_chestplate`), Ignitium Elytra Chestplate (`ignitium_elytra_chestplate`), Ignitium Helmet (`ignitium_helmet`), Ignitium Leggings (`ignitium_leggings`), Infernal Forge (`infernal_forge`), Laser Gatling (`laser_gatling`), Meat Shredder (`meat_shredder`), Monstrous Helm (`monstrous_helm`), Soul Render (`soul_render`), The Annihilator (`the_annihilator`), The Immolator (`the_immolator`), The Incinerator (`the_incinerator`), Tidal Claws (`tidal_claws`), Void Forge (`void_forge`)

**Цифры → убрать:** Black Steel Axe (`black_steel_axe`), Black Steel Hoe (`black_steel_hoe`), Black Steel Pickaxe (`black_steel_pickaxe`), Black Steel Shovel (`black_steel_shovel`), Black Steel Sword (`black_steel_sword`)

**Проверить:** Bone Reptile Chestplate (`bone_reptile_chestplate`), Bone Reptile Helmet (`bone_reptile_helmet`)

## deep_aether
**Особенные → крафт через Create:** Blade of Luck (`blade_of_luck`), Bronze Compass (`bronze_compass`), Gold Compass (`gold_compass`), Silver Compass (`silver_compass`), Skyjade Axe (`skyjade_axe`), Skyjade Boots (`skyjade_boots`), Skyjade Chestplate (`skyjade_chestplate`), Skyjade Gloves (`skyjade_gloves`), Skyjade Helmet (`skyjade_helmet`), Skyjade Hoe (`skyjade_hoe`), Skyjade Leggings (`skyjade_leggings`), Skyjade Pickaxe (`skyjade_pickaxe`), Skyjade Shovel (`skyjade_shovel`), Skyjade Sword (`skyjade_sword`), Storm Bow (`storm_bow`), Storm Sword (`storm_sword`), Stratus Axe (`stratus_axe`), Stratus Helmet (`stratus_helmet`), Stratus Hoe (`stratus_hoe`), Stratus Pickaxe (`stratus_pickaxe`), Stratus Shovel (`stratus_shovel`), Stratus Sword (`stratus_sword`), Wind Shield (`wind_shield`)

**Цифры → убрать:** Skyjade Shield (`skyjade_shield`), Stormforged Boots (`stormforged_boots`), Stormforged Chestplate (`stormforged_chestplate`), Stormforged Gloves (`stormforged_gloves`), Stormforged Helmet (`stormforged_helmet`), Stormforged Leggings (`stormforged_leggings`), Stratus Boots (`stratus_boots`), Stratus Chestplate (`stratus_chestplate`), Stratus Gloves (`stratus_gloves`), Stratus Leggings (`stratus_leggings`), Stratus Shield (`stratus_shield`)

**Проверить:** Skyjade Moa Armor (`skyjade_moa_armor`)

## createqol
**Особенные → крафт через Create:** Refined Radiance Axe (`refined_radiance_axe`), Refined Radiance Boots (`refined_radiance_boots`), Refined Radiance Chestplate (`refined_radiance_chestplate`), Refined Radiance Helmet (`refined_radiance_helmet`), Refined Radiance Hoe (`refined_radiance_hoe`), Refined Radiance Leggings (`refined_radiance_leggings`), Refined Radiance Pickaxe (`refined_radiance_pickaxe`), Refined Radiance Shovel (`refined_radiance_shovel`), Refined Radiance Sword (`refined_radiance_sword`), Shadow Radiance Axe (`shadow_radiance_axe`), Shadow Radiance Boots (`shadow_radiance_boots`), Shadow Radiance Chestplate (`shadow_radiance_chestplate`), Shadow Radiance Helmet (`shadow_radiance_helmet`), Shadow Radiance Hoe (`shadow_radiance_hoe`), Shadow Radiance Leggings (`shadow_radiance_leggings`), Shadow Radiance Pickaxe (`shadow_radiance_pickaxe`), Shadow Radiance Shovel (`shadow_radiance_shovel`), Shadow Radiance Sword (`shadow_radiance_sword`), Shadow Steel Axe (`shadow_steel_axe`), Shadow Steel Boots (`shadow_steel_boots`), Shadow Steel Chestplate (`shadow_steel_chestplate`), Shadow Steel Helmet (`shadow_steel_helmet`), Shadow Steel Hoe (`shadow_steel_hoe`), Shadow Steel Leggings (`shadow_steel_leggings`), Shadow Steel Pickaxe (`shadow_steel_pickaxe`), Shadow Steel Shovel (`shadow_steel_shovel`), Shadow Steel Sword (`shadow_steel_sword`)

## cgs
**Особенные → крафт через Create:** Auto Launcher (`launcher_auto`), Blazegun (`blazegun`), Diamond Hammer Head (`hammer_diamond`), Flintlock (`flintlock`), Flintlock Chambers (`flintlock_chambers`), Gatling (`gatling`), Gatling Shell Press Form (`press_form_gatling`), Hammer: Gun Chamber (`hammer_chamber`), Iron Hammer Head (`hammer_iron`), Item Launcher (`item_launcher`), Launcher (`launcher`), Mortar Barrel (`flintlock_mortar_barrel`), Musket Barrel (`flintlock_long_barrel`), Nailgun (`nailgun`), Nailgun: Splitter Barrel (`nailgun_splitter`), Netherite Hammer Head (`hammer_netherite`), Pneumatic Hammer (`hammer`), Pneumatic Hook (`launcher_hook`), Revolver (`revolver`), Revolver Shell Press Form (`press_form_revolver`), Shotgun (`shotgun`), Shotgun Shell Press Form (`press_form_shotgun`), Shotgun: Automatic Loader (`shotgun_pump`), Shotgun: Long Barrels (`shotgun_long_barrel`), Shotgun: Spreading Barrels (`shotgun_spread_barrel`), Spear (`spear`), Stone Hammer Head (`hammer_stone`)

## aquamirae
**Особенные → крафт через Create:** Abyssal Boots (`abyssal_boots`), Abyssal Brigandine (`abyssal_brigandine`), Abyssal Heaume (`abyssal_heaume`), Abyssal Leggings (`abyssal_leggings`), Abyssal Tiara (`abyssal_tiara`), Dreadwake (`dreadwake`), Echo Compass (`echo_compass`), Jawbreaker (`jawbreaker`), Logbook (`logbook`), Salvager Boots (`salvager_boots`), Salvager Helmet (`salvager_helmet`), Salvager Suit (`salvager_suit`), Salvager Waders (`salvager_waders`), Terrible Boots (`terrible_boots`), Terrible Chestplate (`terrible_chestplate`), Terrible Helmet (`terrible_helmet`), Terrible Leggings (`terrible_leggings`)

**Цифры → убрать:** Dagger of Greed (`dagger_of_greed`), Remnant Saber (`remnant_saber`), Shatterblade (`shatterblade`), Shatterspike (`shatterspike`), Tempest Trident (`tempest_trident`), Terrible Blade (`terrible_blade`), Terrible Cleaver (`terrible_cleaver`), Terrible Fang (`terrible_fang`), Tidepiercer (`tidepiercer`)

## deeperdarker
**Особенные → крафт через Create:** Ancient Compass (`ancient_compass`), Sonorous Staff (`sonorous_staff`), Soul Elytra (`soul_elytra`), Warden Boots (`warden_boots`), Warden Chestplate (`warden_chestplate`), Warden Helmet (`warden_helmet`), Warden Leggings (`warden_leggings`)

**Цифры → убрать:** Resonarium Axe (`resonarium_axe`), Resonarium Hoe (`resonarium_hoe`), Resonarium Pickaxe (`resonarium_pickaxe`), Resonarium Shovel (`resonarium_shovel`), Resonarium Sword (`resonarium_sword`), Warden Axe (`warden_axe`), Warden Hoe (`warden_hoe`), Warden Pickaxe (`warden_pickaxe`), Warden Shovel (`warden_shovel`), Warden Sword (`warden_sword`)

**Проверить:** Resonarium Boots (`resonarium_boots`), Resonarium Chestplate (`resonarium_chestplate`), Resonarium Helmet (`resonarium_helmet`), Resonarium Leggings (`resonarium_leggings`)

## tfmg
**Особенные → крафт через Create:** Advanced Potato Cannon (`advanced_potato_cannon`), Configuration Wrench (`electricians_wrench`), Lead Axe (`lead_axe`), Lead Sword (`lead_sword`), Lithium Blade (`lithium_blade`), Oil Hammer (`oil_hammer`), Quad Potato Cannon (`quad_potato_cannon`)

**Цифры → убрать:** Aluminum Axe (`aluminum_axe`), Aluminum Hoe (`aluminum_hoe`), Aluminum Pickaxe (`aluminum_pickaxe`), Aluminum Shovel (`aluminum_shovel`), Aluminum Sword (`aluminum_sword`), Lead Hoe (`lead_hoe`), Lead Pickaxe (`lead_pickaxe`), Lead Shovel (`lead_shovel`), Mixer Blade (`mixer_blade`), Steel Axe (`steel_axe`), Steel Hoe (`steel_hoe`), Steel Pickaxe (`steel_pickaxe`), Steel Shovel (`steel_shovel`), Steel Sword (`steel_sword`)

## creategoggles
**Особенные → крафт через Create:** Chainmail Backtank (`chainmail_backtank`), Chainmail Backtank Placeable (`chainmail_backtank_placeable`), Diamond Backtank (`diamond_backtank`), Diamond Backtank Placeable (`diamond_backtank_placeable`), Goggle Cardboard Helmet (`goggle_cardboard_helmet`), Goggle Chainmail Helmet (`goggle_chainmail_helmet`), Goggle Diamond Helmet (`goggle_diamond_helmet`), Goggle Diving Helmet (`goggle_diving_helmet`), Goggle Golden Helmet (`goggle_golden_helmet`), Goggle Iron Helmet (`goggle_iron_helmet`), Goggle Leather Helmet (`goggle_leather_helmet`), Goggle Netherite Diving Helmet (`goggle_netherite_diving_helmet`), Goggle Netherite Helmet (`goggle_netherite_helmet`), Goggle Turtle Helmet (`goggle_turtle_helmet`), Golden Backtank (`golden_backtank`), Golden Backtank Placeable (`golden_backtank_placeable`), Iron Backtank (`iron_backtank`), Iron Backtank Placeable (`iron_backtank_placeable`), Leather Backtank (`leather_backtank`), Leather Backtank Placeable (`leather_backtank_placeable`)

## block_factorys_bosses
**Особенные → крафт через Create:** Anchor (`anchor`), Dagger (`dagger`), Dragon Bones Boots (`dragon_bones_boots`), Dragon Bones Chestplate (`dragon_bones_chestplate`), Dragon Bones Leggings (`dragon_bones_leggings`), Helvar's Sword (`knight_sword`), Kraken Cannon (`kraken_cannon_item`), Kraken Trident (`kraken_trident`), Large Sword (`large_sword`), Pirate Saber (`pirate_saber`), Sirok Gauntlet (`sandworm_gauntlet`), Skor Gauntlet (`ice_gauntlet`), Warrior Sword (`warrior_sword`)

**Цифры → убрать:** Enhanced Shield (`enhanced_shield`)

**Проверить:** Dragon Guard Shield (`dragon_guard_shield`), Knight Boots (`knight_boots`), Knight Chestplate (`knight_chestplate`), Knight Helmet (`knight_helmet`), Knight Leggings (`knight_leggings`)

## garnished
**Особенные → крафт через Create:** Blazing Hatchet (`blazing_hatchet`), Brass Hatchet (`brass_hatchet`), Certus Quartz Hatchet (`certus_quartz_hatchet`), Copper Hatchet (`copper_hatchet`), Diamond Hatchet (`diamond_hatchet`), Experience Hatchet (`experience_hatchet`), Fluix Hatchet (`fluix_hatchet`), Gilded Netherite Hatchet (`gilded_netherite_hatchet`), Gilded Quartz Hatchet (`rose_quartz_hatchet`), Golden Hatchet (`golden_hatchet`), Iron Hatchet (`iron_hatchet`), Nether Quartz Hatchet (`nether_quartz_hatchet`), Netherite Hatchet (`netherite_hatchet`), Rose Gold Hatchet (`rose_gold_hatchet`), Stone Hatchet (`stone_hatchet`), Warden Hatchet (`warden_hatchet`), Wooden Hatchet (`wooden_hatchet`), Zinc Hatchet (`zinc_hatchet`)

## mowziesmobs
**Особенные → крафт через Create:** Axe of a Thousand Metals (`wrought_axe`), Blowgun (`blowgun`), Bluff Rod (`bluff_rod`), Geomancer Beads (`geomancer_beads`), Geomancer Belt (`geomancer_belt`), Geomancer Robe (`geomancer_robe`), Geomancer Sandals (`geomancer_sandals`), Geomancer Staff (`sculptor_staff`), Mask of Bliss (`umvuthana_mask_bliss`), Mask of Faith (`umvuthana_mask_faith`), Mask of Fear (`umvuthana_mask_fear`), Mask of Fury (`umvuthana_mask_fury`), Mask of Misery (`umvuthana_mask_misery`), Mask of Rage (`umvuthana_mask_rage`), Naga Fang Dagger (`naga_fang_dagger`), Sol Visage (`sol_visage`), Spear (`spear`), Wrought Helm (`wrought_helmet`)

## create
**Особенные → крафт через Create:** Cardboard Boots (`cardboard_boots`), Cardboard Chestplate (`cardboard_chestplate`), Cardboard Helmet (`cardboard_helmet`), Cardboard Leggings (`cardboard_leggings`), Copper Backtank (`copper_backtank`), Copper Diving Boots (`copper_diving_boots`), Copper Diving Helmet (`copper_diving_helmet`), Netherite Backtank (`netherite_backtank`), Netherite Diving Boots (`netherite_diving_boots`), Netherite Diving Helmet (`netherite_diving_helmet`), Potato Cannon (`potato_cannon`), Wand Of Symmetry (`wand_of_symmetry`), Wrench (`wrench`)

**Цифры → убрать:** Cardboard Sword (`cardboard_sword`)

## apothic_enchanting
**Особенные → крафт через Create:** Tome of Boots (`boots_tome`), Tome of Chestplates (`chestplate_tome`), Tome of Extraction (`extraction_tome`), Tome of Helmets (`helmet_tome`), Tome of Legplates (`leggings_tome`), Tome of Scrapping (`scrap_tome`), Tome of Superior Scrapping (`improved_scrap_tome`), Tome of Weaponry (`weapon_tome`), Tome of the Archers (`bow_tome`), Tome of the Fishers (`fishing_tome`), Tome of the Miners (`pickaxe_tome`), Tome of the Others (`other_tome`)

**Цифры → убрать:** Inert Trident (`inert_trident`)

## createbigcannons
**Особенные → крафт через Create:** Block Armor Inspection Tool (`block_armor_inspection_tool`), Cannon Crafting Wand (`cannon_crafting_wand`), Cannon Welder (`cannon_welder`), Ram Rod (`ram_rod`)

**Цифры → убрать:** Bronze Autocannon Breech Extractor (`bronze_autocannon_breech_extractor`), Cast Iron Autocannon Breech Extractor (`cast_iron_autocannon_breech_extractor`), Guncotton (`guncotton`), Packed Guncotton (`packed_guncotton`), Packed Gunpowder (`packed_gunpowder`), Pair of Cannon Wheels (`pair_of_cannon_wheels`), Pinch of Gunpowder (`gunpowder_pinch`), Steel Autocannon Breech Extractor (`steel_autocannon_breech_extractor`)

## create_things_and_misc
**Особенные → крафт через Create:** Brass Knife (`brass_knife`), Copper Knife (`copper_knife`), Spout Gun (`spout_gun_chocolate`), Spout Gun (`spout_gun`), Spout Gun (`spout_gun_water`), Spout Gun (`spout_gun_lava`), Spout Gun (`spout_gun_honey`), Spout Gun (`spout_gun_slime`), Spout Gun (`stoup_gun_water`), Sticky Boots (`sticky_boots_boots`), Zinc Knife (`zinc_knife`)

## galosphere
**Особенные → крафт через Create:** Echo Bell (`echo_bell`), Spectre Bound Spyglass (`spectre_bound_spyglass`), Sterling Boots (`sterling_boots`), Sterling Chestplate (`sterling_chestplate`), Sterling Helmet (`sterling_helmet`), Sterling Horse Armor (`sterling_horse_armor`), Sterling Leggings (`sterling_leggings`)

## graveyard
**Особенные → крафт через Create:** Bone Dagger (`bone_dagger`), Staff of Azrata'gul (`red_bone_staff`), Staff of Mynakta'gul (`cyan_bone_staff`), Staff of Nortoq'gul (`black_bone_staff`), Staff of Xaphnok'gul (`purple_bone_staff`), Staff of Zatraq'gul (`white_bone_staff`)

## farmersdelight
**Особенные → крафт через Create:** Diamond Knife (`diamond_knife`), Flint Knife (`flint_knife`), Golden Knife (`golden_knife`), Iron Knife (`iron_knife`), Netherite Knife (`netherite_knife`)

## create_confectionery
**Особенные → крафт через Create:** Candy Cane Axe (`candy_cane_axe`), Candy Cane Hoe (`candy_cane_hoe`), Candy Cane Pickaxe (`candy_cane_pickaxe`), Candy Cane Shovel (`candy_cane_shovel`), Candy Cane Sword (`candy_cane_sword`)

## extendedwrenches
**Цифры → убрать:** Wrench (`nope_fake_wrench_name_i_hate_you_registrate`), Wrench Axis Augment (`wrench_axis_augment`), Wrench Cog Augment (`wrench_cog_augment`), Wrench Handle Augment (`wrench_handle_augment`), Wrench Head Augment (`wrench_head_augment`)

## create_deep_dark
**Особенные → крафт через Create:** Echo Boots (`echo_armor_boots`), Echo Chestplate (`echo_armor_chestplate`), Echo Helmet (`echo_armor_helmet`), Echo Leggings (`echo_armor_leggings`), Echo Sword (`echo_sword`)

## create_the_air_wars
**Особенные → крафт через Create:** Target Gunner Gadget (`target_gunnergadget`)

**Цифры → убрать:** Titanium Axe (`titanium_axe`), Titanium Hoe (`titanium_hoe`), Titanium Pickaxe (`titaniumpickaxe`), Titanium Shovel (`titaniumshovel`)

## aeronautics_utility_objects
**Особенные → крафт через Create:** Brass Universal Joint Rod (`universal_joint_rod`), Creative Pneumatic Rod (`creative_hydraulic_rod`), Giant Hydraulic Rod (`giant_hydraulic_rod`), Pneumatic Rod (`hydraulic_rod`)

## create_submarine
**Особенные → крафт через Create:** Diving Chestplate (`diving_chestplate`), Diving Leggings (`diving_leggings`), Pressure Goggles (`pressure_goggles`), Submarine Staff (`submarine_staff`)

## createnuclear
**Особенные → крафт через Create:** Anti Radiation Boots (`default_anti_radiation_boots`), Anti Radiation Chestplate (`default_anti_radiation_chestplate`), Anti Radiation Helmet (`default_anti_radiation_helmet`), Anti Radiation Leggings (`default_anti_radiation_leggings`)

## cbcmoreshells
**Особенные → крафт через Create:** Dual Cannon Combo (`dual_cannon_combo`), Single Cannon Combo (`single_cannon_combo`)

**Цифры → убрать:** Big Cannon Combo (`big_cannon_combo`)

## dndesires
**Особенные → крафт через Create:** Gatling Breaker (`gatling_breaker`), Handheld Drill (`handheld_drill`), Handheld Saw (`handheld_saw`)

## create_ltab
**Особенные → крафт через Create:** Explorer Map (`explorer_map`), Grot Explorer Map (`grot_map`), Kings Castle Explorer Map (`kings_castle_map`)

## rocketnautics
**Особенные → крафт через Create:** Copper Anchor Boots (`copper_anchor_boots`), Space Helmet (`space_helmet`)

## dnt
**Особенные → крафт через Create:** Trident Trial Boss Key (`trident_trial_boss_key`), Trident Trial Key (`trident_trial_key`)

## advancedperipherals
**Особенные → крафт через Create:** Smart Chestplate (`smart_chestplate`)

## bosses_of_mass_destruction
**Особенные → крафт через Create:** Earthdive Spear (`earthdive_spear`)

## createcasing
**Особенные → крафт через Create:** Recaser (`recaser`)

## artifacts
**Особенные → крафт через Create:** Umbrella (`umbrella`)

## computercraft
**Особенные → крафт через Create:** Printed Book (`printed_book`)

## ntgl
**Особенные → крафт через Create:** Black Powder Rifle (`powdergun`)

## shaolib
**Особенные → крафт через Create:** Shaolib Test Gun (`test_gun`)

## shaolib_munitions
**Особенные → крафт через Create:** Munitions Test Gun (`munitions_test_gun`)

---

## Применено в моде-ядре (2026-11-17)

### Удалено — 87 предметов «просто цифры»
Тег `expedition:removed` (`ExpeditionCore/src/main/resources/data/expedition/tags/item/removed.json`): рецепты удаляются после каждой загрузки данных (92 рецепта), глобальный модификатор лута вычищает их из сундуков и дропа мобов (проверено: 120 заполнений сундука из `twilightforest:hill_1` — ни одного), в JEI/EMI скрыты тегом `c:hidden_from_recipe_viewers`.
- Aether: обычные перчатки (кожа, кольчуга, железо, золото, алмаз, незерит, зейнит), зейнитовая броня.
- Twilight Forest: броня и инструменты из железного дерева и стального листа, арктическая и рыцарская броня, рыцарский щит, броня наги.
- Legendary Monsters: инструменты из эндеритиума, Knight's Sword, Shattered Greatsword, Void Sword, щиты из костей динозавра.
- TFMG: инструменты из алюминия, свинца и стали. Cataclysm: инструменты из чёрной стали. Deeper and Darker: инструменты из резонария и Вардена. The Air War: титановые инструменты. Create: картонный меч. Bosses'Rise: Enhanced Shield. Deep Aether: небесно-нефритовый и стратусовый щиты.

**Не удалено, хотя автоматика пометила «цифрами»** (ошибки разметки или есть механика): материалы Big Cannons (экстракторы, пироксилин, порох, колёса), сеты Aether с бонусом (гравитит, нептун, феникс, валькирия, обсидиан), сапоги часового, посох природы, всё оружие Aquamirae (эффекты в процедурах MCreator), `tfmg:mixer_blade` (деталь машины), `twilightforest:crown_splinter`, `legendary_monsters:somber_key` и `reinforced_monstrous_anchor`, сеты Deep Aether stormforged/stratus и часть оружия Legendary Monsters — проверить отдельно.

### Особенное снаряжение — только через Create
169 рецептов модов с боссами и измерениями (Aether, Deep Aether, Twilight, Legendary Monsters, Cataclysm, Mowzie's, Bosses'Rise, Aquamirae, Deeper and Darker, Galosphere, Graveyard, BoMD, Qliphoth) переведены на **Mechanical Crafter** (`data/expedition/recipe/converted/<мод>/…`), исходные рецепты удаляются (167):
- схема рецепта сохраняется; железные/золотые/медные слитки → листы Create с пресса, палки → валы;
- добавлен ряд с деталью тира: точный механизм Create (Mowzie's, Legendary Monsters, Graveyard — T1) или стальной механизм TFMG (остальные — T2+);
- кузнечные улучшения (шаблон + основа + материал) — тоже на Mechanical Crafter.
Генератор и данные ревизии: `ExpeditionCore/tools/audit/` (`collect_recipes.py` → `gear_recipes_full.json` → `convert_recipes.py`).

Не переведено (особые станки модов — решить вручную): Cataclysm weapon_fusion (Brontes, Gauntlet of Bulwark/Maelstrom, Ignitium Elytra Chestplate, The Immolator, Void Forge), Deep Aether poison_recipe (золотой дротикомёт), Aether enchanting (зачарованный дротикомёт).
