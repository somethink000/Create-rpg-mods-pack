// Minecart contraptions are banned: they automate mining/farming on rails too cheaply.
// The Cart Assembler is the only way to assemble one; couplings exist only to chain such carts.
// Placement is also blocked because assemblers can still appear in structure loot or old inventories.

const BANNED_CART_ITEMS = [
  'create:cart_assembler',
  'create:minecart_coupling',
]

const BANNED_CART_RECIPE_IDS = [
  'create:crafting/kinetics/cart_assembler',
  'create:crafting/curiosities/minecart_coupling',
  // These only turn a contraption cart back into a plain cart; useless without assemblers.
  'create:crafting/kinetics/minecart_from_contraption_cart',
  'create:crafting/kinetics/furnace_minecart_from_contraption_cart',
]

ServerEvents.recipes(event => {
  BANNED_CART_RECIPE_IDS.forEach(recipeId => event.remove({ id: recipeId }))
  BANNED_CART_ITEMS.forEach(itemId => event.remove({ output: itemId }))
})

BlockEvents.placed('create:cart_assembler', event => {
  event.player?.tell(Text.red('Контрапции на вагонетках отключены в этой сборке.'))
  event.cancel()
})
