// Boss loot must be worth a long fight (decision 2026-11-06, see modpack-docs/loot-artifacts.md).
// Every boss keeps its own unique drop and additionally always drops a themed artifact/relic;
// a second themed item drops with BONUS_TROPHY_CHANCE. Junk (bones, coal, stone, a golden sword...) is stripped.
// MultiplayerBosses rolls this loot once more per extra player and packs it into per-player bags.

const BONUS_TROPHY_CHANCE = 0.35

// boss -> [guaranteed trophy, bonus trophy]
const BOSS_TROPHIES = {
  'mowziesmobs:frostmaw': ['artifacts:snowshoes', 'relics:midnight_mantle'],
  'mowziesmobs:ferrous_wroughtnaut': ['relics:shield_of_retaliation', 'artifacts:steadfast_spikes'],
  'mowziesmobs:umvuthi': ['artifacts:flame_pendant', 'relics:leafy_mantle'],
  'mowziesmobs:sculptor': ['artifacts:pocket_piston', 'artifacts:digging_claws'],
  'block_factorys_bosses:yeti': ['artifacts:snowshoes', 'artifacts:steadfast_spikes'],
  'block_factorys_bosses:infernal_dragon': ['artifacts:fire_gauntlet', 'artifacts:flame_pendant'],
  'block_factorys_bosses:kraken': ['relics:jellyfish_necklace', 'artifacts:flippers'],
  'block_factorys_bosses:sandworm': ['artifacts:digging_claws', 'artifacts:running_shoes'],
  'block_factorys_bosses:underworld_knight': ['relics:shield_of_retaliation', 'artifacts:thorn_pendant'],
  'cataclysm:ignis': ['artifacts:fire_gauntlet', 'artifacts:obsidian_skull'],
  'cataclysm:netherite_monstrosity': ['artifacts:obsidian_skull', 'artifacts:power_glove'],
  'cataclysm:the_leviathan': ['relics:jellyfish_necklace', 'artifacts:snorkel'],
  'cataclysm:scylla': ['artifacts:shock_pendant', 'artifacts:flippers'],
  'cataclysm:ender_guardian': ['artifacts:crystal_heart', 'relics:reflective_necklace'],
  'cataclysm:the_harbinger': ['artifacts:withered_bracelet', 'relics:ring_of_the_seven_deadly_sins'],
  'cataclysm:ancient_remnant': ['artifacts:lucky_scarf', 'relics:golden_tooth'],
  'cataclysm:maledictus': ['relics:ghostly_mantle', 'artifacts:cross_necklace'],
  'aquamirae:captain_cornelia': ['artifacts:anglers_hat', 'artifacts:charm_of_sinking'],
  'aquamirae:maze_mother': ['artifacts:charm_of_sinking', 'relics:jellyfish_necklace'],
  'bosses_of_mass_destruction:lich': ['artifacts:cross_necklace', 'relics:experience_disperser'],
  'bosses_of_mass_destruction:obsidilith': ['artifacts:crystal_heart', 'artifacts:steadfast_spikes'],
  'bosses_of_mass_destruction:gauntlet': ['artifacts:power_glove', 'artifacts:feral_claws'],
  'bosses_of_mass_destruction:void_blossom': ['relics:sphere_of_self_sacrifice', 'artifacts:antidote_vessel'],
  'graveyard:lich': ['relics:ghostly_mantle', 'artifacts:cross_necklace'],
  'twilightforest:lich': ['artifacts:cross_necklace', 'relics:experience_disperser'],
  'twilightforest:naga': ['artifacts:feral_claws', 'artifacts:vampiric_glove'],
  'twilightforest:hydra': ['artifacts:vampiric_glove', 'artifacts:fire_gauntlet'],
  'twilightforest:minoshroom': ['artifacts:power_glove', 'relics:hunting_belt'],
  'twilightforest:knight_phantom': ['relics:shield_of_retaliation', 'artifacts:thorn_pendant'],
  'twilightforest:ur_ghast': ['artifacts:flame_pendant', 'artifacts:obsidian_skull'],
  'twilightforest:alpha_yeti': ['artifacts:snowshoes', 'relics:midnight_mantle'],
  'twilightforest:snow_queen': ['relics:cut_glass_boot', 'artifacts:snowshoes'],
  'aether:slider': ['artifacts:lucky_scarf', 'artifacts:umbrella'],
  'aether:valkyrie_queen': ['artifacts:cloud_in_a_bottle', 'artifacts:crystal_heart'],
  'aether:sun_spirit': ['artifacts:flame_pendant', 'relics:clot_of_time'],
  'deep_aether:eots_controller': ['artifacts:umbrella', 'artifacts:lucky_scarf'],
  'fdbosses:chesed': ['artifacts:shock_pendant', 'relics:reflective_necklace'],
  'fdbosses:geburah': ['relics:reflective_necklace', 'relics:sphere_of_self_sacrifice'],
  'fdbosses:malkuth': ['artifacts:fire_gauntlet', 'artifacts:snowshoes'],
  'legendary_monsters:withered_abomination': ['artifacts:withered_bracelet', 'artifacts:thorn_pendant'],
  'minecraft:wither': ['artifacts:withered_bracelet', 'relics:ring_of_the_seven_deadly_sins'],
  'minecraft:ender_dragon': ['artifacts:crystal_heart', 'relics:clot_of_time'],
}

const JUNK = [
  'minecraft:bone',
  'minecraft:rotten_flesh',
  'minecraft:coal',
  'minecraft:charcoal',
  'minecraft:iron_nugget',
  'minecraft:golden_sword',
  'aether:carved_stone',
  'aether:cold_aercloud',
]

LootJS.modifiers(event => {
  Object.entries(BOSS_TROPHIES).forEach(([boss, [trophy, bonus]]) => {
    const loot = event.addEntityModifier(boss)
    JUNK.forEach(item => loot.removeLoot(item))
    loot.addLoot(trophy)
    event.addEntityModifier(boss).randomChance(BONUS_TROPHY_CHANCE).addLoot(bonus)
  })
})
