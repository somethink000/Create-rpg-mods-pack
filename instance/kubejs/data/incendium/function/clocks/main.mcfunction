# Expedition: Incendium's per-tick clock, the same work, but its mob passes (each a scan of every loaded entity of the
# world) only run while a player is in or near the Nether realm, where its mobs live ($expedition_nether, set once a
# second by the Expedition mod: net.expedition.world.realm.NetherPresence; always 1 outside the realm world).
schedule function incendium:clocks/main 1t replace
function incendium:technical/main
execute as @a at @s run function incendium:player/main
function incendium:castle/main
execute if score $expedition_nether in.dummy matches 1 run function incendium:clocks/expedition_mobs
execute if score $expedition_nether in.dummy matches 1 run function incendium:clocks/expedition_frozen
execute if score $expedition_nether in.dummy matches 1 run function incendium:clocks/expedition_other
execute if entity @a[tag=nbs_borderofli] run function incendium:border_of_life/tick
# The shields are held by players: checked on players (a score test on every entity of the world was most of this clock).
execute if entity @a[scores={in.cd_shield=1..},predicate=incendium:holding/radiation_shield] run effect clear @a[scores={in.cd_shield=1..},predicate=incendium:holding/radiation_shield] poison
execute if entity @a[scores={in.cd_shield=1..},predicate=incendium:holding/necrotic_shield] run effect clear @a[scores={in.cd_shield=1..},predicate=incendium:holding/necrotic_shield] wither
