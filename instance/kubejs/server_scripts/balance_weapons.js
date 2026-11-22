// Weapon tiers for Create: Gunsmithing (see modpack-docs/balance.md, section 7.1).
// Flintlock stays an early, weak gun; revolver/shotgun already need TFMG steel (tier 2).
// Automatic and rocket weapons move to the TFMG heavy-industry tier: the Create precision mechanism
// is swapped for the TFMG steel mechanism, and the rocket launcher body needs a heavy machinery casing.

const PRECISION_MECHANISM = 'create:precision_mechanism'
const STEEL_MECHANISM = 'tfmg:steel_mechanism'
const ANDESITE_ALLOY = 'create:andesite_alloy'
const HEAVY_MACHINERY_CASING = 'tfmg:heavy_machinery_casing'

const STEEL_MECHANISM_RECIPES = [
  'cgs:mechanical_crafting/gatling',
  'cgs:mechanical_crafting/auto_fire',
  'cgs:mechanical_crafting/launcher_auto',
]

const HEAVY_CASING_RECIPES = [
  'cgs:mechanical_crafting/launcher',
]

ServerEvents.recipes(event => {
  STEEL_MECHANISM_RECIPES.forEach(recipeId => {
    event.replaceInput({ id: recipeId }, PRECISION_MECHANISM, STEEL_MECHANISM)
  })
  HEAVY_CASING_RECIPES.forEach(recipeId => {
    event.replaceInput({ id: recipeId }, ANDESITE_ALLOY, HEAVY_MACHINERY_CASING)
  })
})
