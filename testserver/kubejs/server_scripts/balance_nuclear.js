// Nuclear power is end-game (see modpack-docs/balance.md, section 14a).
// - Uranium and thorium ore only generate on the Moon (datapack overrides in kubejs/data/), so fuel needs a space trip.
// - Crushing granite gave uranium powder, i.e. fuel straight from Overworld stone; that recipe is removed.
// - Reactor parts need Moon titanium (Resilient Casing) and the TFMG steel mechanism on top of the nether star /
//   netherite the controller already requires.

const RESILIENT_CASING = 'rocketnautics:titanium_casing'
const BRASS_CASING = 'create:brass_casing'
const NUCLEAR_PRECISION_MECHANISM = 'create:precision_mechanism'
const NUCLEAR_STEEL_MECHANISM = 'tfmg:steel_mechanism'

ServerEvents.recipes(event => {
  event.remove({ id: 'createnuclear:crushing/granite' })

  event.replaceInput({ id: 'createnuclear:item_application/reactor_casing_from_steel_and_brass_casing' }, BRASS_CASING, RESILIENT_CASING)
  event.replaceInput({ id: 'createnuclear:mechanical_crafting/reactor_core' }, NUCLEAR_PRECISION_MECHANISM, NUCLEAR_STEEL_MECHANISM)
  event.replaceInput({ id: 'createnuclear:crafting/reactor_blueprint_item' }, NUCLEAR_PRECISION_MECHANISM, NUCLEAR_STEEL_MECHANISM)
})
