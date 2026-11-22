// Rubber instead of dried kelp in technical recipes (belts, seals, hoses, tires).
// Rubberworks already overrides the Create recipes below, but two datapacks shipping the same
// recipe file resolve by load order, so we enforce the result here.
// Food, decoration and algae-themed recipes (e.g. Deep Seas membrane) intentionally keep kelp.

const DRIED_KELP = 'minecraft:dried_kelp'
const DRIED_KELP_BLOCK = 'minecraft:dried_kelp_block'
const RUBBER_SHEET = 'rubberworks:rubber_sheet'
const RUBBER_BLOCK = 'rubberworks:rubber_block'

const RUBBER_RECIPE_IDS = [
  // Create
  'create:crafting/kinetics/belt_connector',
  'create:crafting/kinetics/spout',
  'create:crafting/kinetics/elevator_pulley',
  'create:crafting/kinetics/hose_pulley',
  'create:crafting/logistics/andesite_funnel',
  'create:crafting/logistics/brass_funnel',
  'create:crafting/logistics/andesite_tunnel',
  'create:crafting/logistics/brass_tunnel',
  // Create Aeronautics (Offroad tires, potato cannon seal)
  'offroad:tire',
  'offroad:small_tire',
  'offroad:monstrous_tire',
  'aeronautics:mechanical_crafting/mounted_potato_cannon',
  // Aeronautics: Transmission & Linkage (joint boot, hydraulic seals)
  'aeronautics_utility_objects:brass_universal_joint',
  'aeronautics_utility_objects:giant_hydraulic_rod',
  // Create: Misc and Things
  'create_things_and_misc:card_reader_craft_2',
  'create_things_and_misc:sprinklerheadcraft',
  // TFMG
  'tfmg:crafting/materials/concrete_hose',
]

ServerEvents.recipes(event => {
  RUBBER_RECIPE_IDS.forEach(recipeId => {
    event.replaceInput({ id: recipeId }, DRIED_KELP, RUBBER_SHEET)
    event.replaceInput({ id: recipeId }, DRIED_KELP_BLOCK, RUBBER_BLOCK)
  })
})
