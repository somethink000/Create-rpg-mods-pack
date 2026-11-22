// One steel in the pack: TFMG. Gunsmithing and Big Cannons ship their own steel with cheap
// mixer recipes (iron + charcoal / iron + coal), which would bypass the TFMG blast furnace.
// Their steel stops being obtainable, recipes asking for it take TFMG steel instead,
// and it leaves the shared c:*/steel tags so tag-based recipes cannot accept it either.
// Big Cannons melting/casting keeps working: it melts anything in c:ingots/steel, i.e. TFMG steel.

// Foreign steel item -> TFMG equivalent (TFMG tags tfmg:heavy_plate as c:plates/steel).
const STEEL_REPLACEMENTS = {
  'cgs:steel_ingot': 'tfmg:steel_ingot',
  'cgs:steel_nugget': 'tfmg:steel_nugget',
  'cgs:steel_sheet': 'tfmg:heavy_plate',
  'cgs:steel_block': 'tfmg:steel_block',
  'createbigcannons:steel_ingot': 'tfmg:steel_ingot',
  'createbigcannons:steel_scrap': 'tfmg:steel_nugget',
  'createbigcannons:steel_block': 'tfmg:steel_block',
}

const STEEL_TAGS = [
  'c:ingots/steel',
  'c:nuggets/steel',
  'c:plates/steel',
  'c:storage_blocks/steel',
]

const FOREIGN_STEEL_ITEMS = Object.keys(STEEL_REPLACEMENTS)

ServerEvents.tags('item', event => {
  STEEL_TAGS.forEach(tag => event.remove(tag, FOREIGN_STEEL_ITEMS))
})

ServerEvents.recipes(event => {
  // Removes the cheap mixer recipes, compression/decompression and Big Cannons molten-steel compacting.
  FOREIGN_STEEL_ITEMS.forEach(itemId => event.remove({ output: itemId }))

  Object.entries(STEEL_REPLACEMENTS).forEach(([foreignItem, tfmgItem]) => {
    event.replaceInput({}, foreignItem, tfmgItem)
  })
})
