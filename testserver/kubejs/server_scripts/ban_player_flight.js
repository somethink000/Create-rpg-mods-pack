// No personal flight in this pack: travelling through the air is what ships are for.
// Three layers, because flight comes from items, effects, affixes, blocks and mounts:
//   1. recipes that create flight gear are removed;
//   2. pure flight items are deleted as soon as they enter an inventory (loot, mob drops, trades);
//   3. every tick, survival players lose creative flight and elytra-style gliding whatever its source
//      (Apotheosis "Flying" potion/affix, Relics "Flight" effect, BoMD levitation block, elytra chestplates...).
// Elytra chestplates go too (Ignitium); the Radiance chestplate's elytra/propeller slots are off in createqol-server.toml.

const FLIGHT_RECIPE_IDS = [
  'deeperdarker:soul_elytra',
  'cataclysm:weapon_infusion/ignitium_elytra_chestplate',
  'createqol:ability/elytra',
  'createqol:ability/propeller',
  'incendium:upgrade_elytra',
  'rocketnautics:crafting/equipment/copper_leg_thrusters',
  'rocketnautics:crafting/equipment/jetpack',
]

const BANNED_FLIGHT_ITEMS = [
  'minecraft:elytra',
  'deeperdarker:soul_elytra',
  'twilightforest:travellers_wings',
  'rocketnautics:copper_leg_thrusters',
  'rocketnautics:jetpack',
  'artifacts:helium_flamingo',
  'bosses_of_mass_destruction:levitation_block',
  'cataclysm:ignitium_elytra_chestplate',
]

// Riding these lets a player fly freely.
const BANNED_FLYING_MOUNTS = [
  'aether:phyg',
  'aether:flying_cow',
]

// Short-range noclip flight through terrain; usable items are blocked instead of deleted.
const BANNED_USE_ITEMS = [
  'fdbosses:phase_sphere',
]

const FLIGHT_BAN_MESSAGE = 'Личный полёт отключён в этой сборке — летайте на кораблях.'

ServerEvents.recipes(event => {
  FLIGHT_RECIPE_IDS.forEach(recipeId => event.remove({ id: recipeId }))
  BANNED_FLIGHT_ITEMS.forEach(itemId => event.remove({ output: itemId }))
})

PlayerEvents.inventoryChanged(event => {
  if (BANNED_FLIGHT_ITEMS.includes(event.item.id)) {
    event.item.shrink(event.item.count)
    event.player.tell(Text.red(FLIGHT_BAN_MESSAGE))
  }
})

BANNED_USE_ITEMS.forEach(itemId => {
  ItemEvents.rightClicked(itemId, event => {
    event.player.tell(Text.red(FLIGHT_BAN_MESSAGE))
    event.cancel()
  })
})

BlockEvents.placed('bosses_of_mass_destruction:levitation_block', event => {
  event.player?.tell(Text.red(FLIGHT_BAN_MESSAGE))
  event.cancel()
})

PlayerEvents.tick(event => {
  const player = event.player
  if (player.isCreative() || player.isSpectator()) {
    return
  }
  if (player.isFallFlying()) {
    player.stopFallFlying()
  }
  const abilities = player.abilities
  if (abilities.mayfly || abilities.flying) {
    abilities.mayfly = false
    abilities.flying = false
    player.onUpdateAbilities()
  }
  const vehicle = player.vehicle
  if (vehicle && BANNED_FLYING_MOUNTS.includes(vehicle.type)) {
    player.stopRiding()
    player.tell(Text.red(FLIGHT_BAN_MESSAGE))
  }
})
