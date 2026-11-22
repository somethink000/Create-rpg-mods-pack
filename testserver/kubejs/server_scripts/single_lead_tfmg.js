// One lead in the pack: TFMG. Gunsmithing and Create Nuclear ship their own lead ore and metal.
// Unlike steel, their lead is not a cheap shortcut — it is a duplicate — so recipes are redirected
// rather than deleted: every recipe that makes or uses their lead now makes or uses TFMG lead
// (this keeps chains such as washing crushed lead working). Their ore generation is turned off by
// datapack overrides in kubejs/data/{cgs,createnuclear}/neoforge/biome_modifier/.

const LEAD_REPLACEMENTS = {
  'cgs:lead_ingot': 'tfmg:lead_ingot',
  'cgs:lead_nugget': 'tfmg:lead_nugget',
  'cgs:lead_block': 'tfmg:lead_block',
  'cgs:raw_lead': 'tfmg:raw_lead',
  'cgs:raw_lead_block': 'tfmg:raw_lead_block',
  'cgs:lead_ore': 'tfmg:lead_ore',
  'cgs:deepslate_lead_ore': 'tfmg:deepslate_lead_ore',
  'createnuclear:lead_ingot': 'tfmg:lead_ingot',
  'createnuclear:lead_nugget': 'tfmg:lead_nugget',
  'createnuclear:lead_block': 'tfmg:lead_block',
  'createnuclear:raw_lead': 'tfmg:raw_lead',
  'createnuclear:raw_lead_block': 'tfmg:raw_lead_block',
  'createnuclear:lead_ore': 'tfmg:lead_ore',
  'createnuclear:deepslate_lead_ore': 'tfmg:deepslate_lead_ore',
}

const LEAD_TAGS = [
  'c:ingots/lead',
  'c:nuggets/lead',
  'c:ores/lead',
  'c:raw_materials/lead',
  'c:storage_blocks/lead',
  'c:storage_blocks/raw_lead',
  'c:raw_storage_blocks/lead',
  'c:raw_lead/lead',
]

const FOREIGN_LEAD_ITEMS = Object.keys(LEAD_REPLACEMENTS)

ServerEvents.tags('item', event => {
  LEAD_TAGS.forEach(tag => event.remove(tag, FOREIGN_LEAD_ITEMS))
})

ServerEvents.recipes(event => {
  Object.entries(LEAD_REPLACEMENTS).forEach(([foreignItem, tfmgItem]) => {
    event.replaceOutput({}, foreignItem, tfmgItem)
    event.replaceInput({}, foreignItem, tfmgItem)
  })
})
