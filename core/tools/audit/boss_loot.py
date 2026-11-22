# What every boss's entity loot table drops now: items, split into kept special gear vs materials.
import zipfile, json, re, sys, os
mods, gear_path = sys.argv[1:3]
gear = {json.loads(l)['id']: json.loads(l)['verdict'] for l in open(gear_path)}
BOSSES = ['cataclysm:ignis','cataclysm:netherite_monstrosity','cataclysm:the_harbinger','cataclysm:the_leviathan','cataclysm:ancient_remnant','cataclysm:maledictus',
 'cataclysm:scylla','cataclysm:ender_guardian','mowziesmobs:ferrous_wroughtnaut','mowziesmobs:frostmaw','mowziesmobs:umvuthi','bosses_of_mass_destruction:lich',
 'bosses_of_mass_destruction:obsidilith','bosses_of_mass_destruction:gauntlet','bosses_of_mass_destruction:void_blossom','fdbosses:chesed','fdbosses:geburah','fdbosses:malkuth',
 'block_factorys_bosses:underworld_knight','block_factorys_bosses:kraken','block_factorys_bosses:yeti','block_factorys_bosses:sandworm','block_factorys_bosses:infernal_dragon',
 'aquamirae:captain_cornelia','aquamirae:maze_mother','legendary_monsters:overgrown_colossus','legendary_monsters:frostbitten_golem','legendary_monsters:lava_eater',
 'legendary_monsters:skeletosaurus','legendary_monsters:withered_abomination','legendary_monsters:shulker_mimic','legendary_monsters:cloud_golem','legendary_monsters:posessed_paladin',
 'legendary_monsters:ancient_guardian','twilightforest:naga','twilightforest:lich','twilightforest:minoshroom','twilightforest:hydra','twilightforest:knight_phantom',
 'twilightforest:ur_ghast','twilightforest:alpha_yeti','twilightforest:snow_queen','minecraft:elder_guardian','minecraft:wither','minecraft:ender_dragon']
tables = {}
for name in os.listdir(mods):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        m = re.match(r'data/([^/]+)/loot_tables?/entit(?:y|ies)/(.+)\.json$', n)
        if m: tables[f'{m.group(1)}:{m.group(2)}'] = z.read(n).decode('utf-8', 'ignore')
for boss in BOSSES:
    text = tables.get(boss)
    if text is None:
        print(f'{boss:<44} (no loot table in jars)'); continue
    items = sorted(set(re.findall(r'"name":\s*"([a-z0-9_]+:[a-z0-9_/]+)"', text)) - {'minecraft:empty'})
    g = [i.split(':')[1] for i in items if gear.get(i) in ('special', 'stats?')]
    o = [i.split(':')[1] for i in items if i not in gear]
    print(f'{boss:<44} GEAR: {", ".join(g) or "-"} | OTHER: {", ".join(o)[:120]}')
