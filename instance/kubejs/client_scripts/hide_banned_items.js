// Hide banned items from JEI/EMI so players do not plan around them.
// Keep in sync with the ban scripts in server_scripts/.

const HIDDEN_ITEMS = [
  // ban_minecart_contraptions.js
  'create:cart_assembler',
  'create:minecart_coupling',
  // ban_cheap_fe_converters.js
  'createthrusters:alternator',
  // single_steel_tfmg.js
  'cgs:steel_ingot',
  'cgs:steel_nugget',
  'cgs:steel_sheet',
  'cgs:steel_block',
  'createbigcannons:steel_ingot',
  'createbigcannons:steel_scrap',
  'createbigcannons:steel_block',
  // ban_player_flight.js
  'minecraft:elytra',
  'deeperdarker:soul_elytra',
  'twilightforest:travellers_wings',
  'rocketnautics:copper_leg_thrusters',
  'rocketnautics:jetpack',
  'artifacts:helium_flamingo',
  'bosses_of_mass_destruction:levitation_block',
  'cataclysm:ignitium_elytra_chestplate',
  // single_lead_tfmg.js
  'cgs:lead_ingot',
  'cgs:lead_nugget',
  'cgs:lead_block',
  'cgs:raw_lead',
  'cgs:raw_lead_block',
  'cgs:lead_ore',
  'cgs:deepslate_lead_ore',
  'createnuclear:lead_ingot',
  'createnuclear:lead_nugget',
  'createnuclear:lead_block',
  'createnuclear:raw_lead',
  'createnuclear:raw_lead_block',
  'createnuclear:lead_ore',
  'createnuclear:deepslate_lead_ore',
]

RecipeViewerEvents.removeEntries('item', event => {
  HIDDEN_ITEMS.forEach(itemId => event.remove(itemId))
})
