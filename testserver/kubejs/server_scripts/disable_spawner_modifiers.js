// Apotheosis 8.9 hard-requires Apothic Spawners, which lets players upgrade a found spawner in place
// (more mobs, no player nearby needed, ignore light/conditions, redstone control...).
// That is a free mob/loot farm, so every spawner modifier recipe is removed: spawners stay vanilla.

ServerEvents.recipes(event => {
  event.remove({ type: 'apothic_spawners:spawner_modifier' })
})
