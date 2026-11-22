// Boss difficulty (see modpack-docs/balance.md, section 8).
// - Every boss gets more health and hits harder; final bosses (route T5-T6) more than the rest.
// - Bosses cannot be deleted in seconds by Big Cannons / missiles: explosions deal reduced damage
//   and no single hit may take more than a fixed share of the boss's max health.
// - Bosses that their mods forgot to tag are added to c:bosses, so MultiplayerBosses scales them too.
// Numbers are a first pass and must be tuned by test fights (balance.md, section 14).

const HEALTH_ATTRIBUTE = 'minecraft:generic.max_health'
const HEALTH_MODIFIER_ID = 'kubejs:boss_balance_health'
const SCALED_FLAG = 'kubejs_boss_scaled'

const BOSS_HEALTH_MULTIPLIER = 2.0
const FINAL_BOSS_HEALTH_MULTIPLIER = 2.5
const BOSS_DAMAGE_MULTIPLIER = 1.3
const EXPLOSION_DAMAGE_TAKEN_MULTIPLIER = 0.35
const MAX_HIT_SHARE_OF_MAX_HEALTH = 0.06

const FINAL_BOSSES = [
  'minecraft:wither',
  'cataclysm:the_harbinger',
  'cataclysm:the_leviathan',
  'cataclysm:ancient_remnant',
  'cataclysm:maledictus',
  'cataclysm:scylla',
  'twilightforest:snow_queen',
  'twilightforest:ur_ghast',
  'fdbosses:geburah',
  'fdbosses:malkuth',
  'fdbosses:chesed',
]

const BOSSES = FINAL_BOSSES.concat([
  'minecraft:ender_dragon',
  'bosses_of_mass_destruction:obsidilith',
  'bosses_of_mass_destruction:void_blossom',
  'cataclysm:ignis',
  'cataclysm:netherite_monstrosity',
  'cataclysm:ender_guardian',
  'bosses_of_mass_destruction:lich',
  'bosses_of_mass_destruction:gauntlet',
  'block_factorys_bosses:underworld_knight',
  'block_factorys_bosses:sandworm',
  'block_factorys_bosses:yeti',
  'block_factorys_bosses:infernal_dragon',
  'block_factorys_bosses:kraken',
  'legendary_monsters:the_obliterator',
  'legendary_monsters:cloud_golem',
  'legendary_monsters:posessed_paladin',
  'legendary_monsters:overgrown_colossus',
  'legendary_monsters:withered_abomination',
  'legendary_monsters:ancient_guardian',
  'legendary_monsters:frostbitten_golem',
  'legendary_monsters:lava_eater',
  'legendary_monsters:warped_fungussus',
  'legendary_monsters:skeletosaurus',
  'legendary_monsters:dune_sentinel',
  'legendary_monsters:endersent',
  'graveyard:lich',
  'mowziesmobs:frostmaw',
  'mowziesmobs:ferrous_wroughtnaut',
  'mowziesmobs:umvuthi',
  'mowziesmobs:sculptor',
  'twilightforest:naga',
  'twilightforest:lich',
  'twilightforest:minoshroom',
  'twilightforest:hydra',
  'twilightforest:knight_phantom',
  'twilightforest:alpha_yeti',
  'aether:slider',
  'aether:valkyrie_queen',
  'aether:sun_spirit',
  'deep_aether:eots_controller',
  'aquamirae:captain_cornelia',
  'aquamirae:maze_mother',
])

// Present in BOSSES but missing from the mods' own c:bosses tags.
const UNTAGGED_BOSSES = [
  'fdbosses:chesed',
  'fdbosses:geburah',
  'fdbosses:malkuth',
  'graveyard:lich',
  'aquamirae:captain_cornelia',
  'aquamirae:maze_mother',
  'legendary_monsters:overgrown_colossus',
  'legendary_monsters:withered_abomination',
  'legendary_monsters:ancient_guardian',
  'legendary_monsters:frostbitten_golem',
  'legendary_monsters:lava_eater',
  'legendary_monsters:warped_fungussus',
  'legendary_monsters:skeletosaurus',
  'legendary_monsters:dune_sentinel',
  'legendary_monsters:endersent',
]

const EXPLOSION_DAMAGE_IDS = ['explosion', 'explosion.player']

ServerEvents.tags('entity_type', event => {
  event.add('c:bosses', UNTAGGED_BOSSES)
})

EntityEvents.spawned(event => {
  const entity = event.entity
  if (!BOSSES.includes(entity.type) || entity.persistentData.getBoolean(SCALED_FLAG)) {
    return
  }
  // The flag makes the bonus one-time: modifiers are saved with the entity, and re-healing on every
  // chunk reload would let players reset a fight by walking away.
  const multiplier = FINAL_BOSSES.includes(entity.type) ? FINAL_BOSS_HEALTH_MULTIPLIER : BOSS_HEALTH_MULTIPLIER
  entity.modifyAttribute(HEALTH_ATTRIBUTE, HEALTH_MODIFIER_ID, multiplier - 1, 'add_multiplied_base')
  entity.health = entity.maxHealth
  entity.persistentData.putBoolean(SCALED_FLAG, true)
})

EntityEvents.beforeHurt(event => {
  const target = event.entity
  const attacker = event.source.entity

  if (attacker && BOSSES.includes(attacker.type)) {
    event.newDamage = event.newDamage * BOSS_DAMAGE_MULTIPLIER
    return
  }
  if (!BOSSES.includes(target.type)) {
    return
  }
  let damage = event.newDamage
  if (EXPLOSION_DAMAGE_IDS.includes(event.source.msgId)) {
    damage = damage * EXPLOSION_DAMAGE_TAKEN_MULTIPLIER
  }
  event.newDamage = Math.min(damage, target.maxHealth * MAX_HIT_SHARE_OF_MAX_HEALTH)
})
