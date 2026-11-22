// FE must come only from TFMG electricity. The Gadgets & Gizmos alternator turns any rotation
// (including free water wheels) into FE for a cheap recipe, so it is removed.
// Placement is also blocked in case one turns up in loot or an old inventory.

const BANNED_FE_CONVERTER = 'createthrusters:alternator'

ServerEvents.recipes(event => {
  event.remove({ output: BANNED_FE_CONVERTER })
})

BlockEvents.placed(BANNED_FE_CONVERTER, event => {
  event.player?.tell(Text.red('Этот конвертер в FE отключён в сборке: используйте электрику TFMG.'))
  event.cancel()
})
