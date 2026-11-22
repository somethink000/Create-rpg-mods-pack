# Generates the story's data from story_text.py and book_text.py (run from anywhere):
#   route steps for Twilight/Aether, lang keys (ru_ru, en_us), the advancement tree "The Stitched World",
#   item models, and the Patchouli guide book.
# Acts and their key bosses mirror route/Acts.java; keep both in step.
import json, os, subprocess, sys, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from story_text import SPEAKERS, ACTS, OUTROS, FINALE, SEALS, MISC, NEW_STEPS, STEP_SCENES, STEP_TEXT_OVERRIDES, SEED, PROLOGUE, GUARDIANS, GARRISONS
from book_text import BOOK, CATEGORIES, ENTRIES, STORY_ENTRIES

CORE = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
RES = os.path.join(CORE, 'src/main/resources')
DATA = os.path.join(RES, 'data/expedition')
ASSETS = os.path.join(RES, 'assets/expedition')
TIER_RING = {'T0': (500, 1600), 'T1': (1400, 3200), 'T2': (2800, 5000), 'T3': (4500, 7000), 'T4': (6500, 9000), 'T5': (8500, 11000), 'T6': (10500, 13500)}
# Must match Acts.java: act -> keystone step ids. An act opens when three quarters of the one before are done.
ACT_DEFS = {1: ['03_wrought_chamber', '10_ocean_monument'], 2: ['11_pirate_ship', '05_mossy_temple'],
            3: ['06_underworld_arena', '18_ender_dragon'], 4: ['19_ruined_citadel', '27_lich_tower'],
            5: ['14_burning_arena', '15_soul_blacksmith'], 6: ['21_ancient_factory', '22_sunken_city', '31_wither'],
            7: ['24_malkuth', '25_chesed', '26_geburah']}

ru, en = {}, {}
def put(key, pair):
    ru['expedition.' + key], en['expedition.' + key] = pair

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(json.dumps(obj, indent=2, ensure_ascii=False) + '\n')

# --- texts ---
for key, pair in {**SPEAKERS, **MISC}.items():
    put(key, pair)
for act, a in ACTS.items():
    put(f'story.act.{act}.title', a['title'])
    put(f'story.act.{act}.subtitle', a['subtitle'])
    for i, line in enumerate(a['lines'], 1):
        put(f'story.act.{act}.{i}', line)
for step, lines in OUTROS.items():
    for i, line in enumerate(lines, 1):
        put(f'story.outro.{step}.{i}', line)
for i, line in enumerate(FINALE['lines'], 1):
    put(f'story.finale.{i}', line)
put('story.prologue.title', PROLOGUE['title'])
put('story.prologue.loading', PROLOGUE['loading'])
for place, lines in PROLOGUE.items():
    if place not in ('title', 'loading'):
        for i, line in enumerate(lines, 1):
            put(f'story.prologue.{place}.{i}', line)
for key, pair in SEED.items():
    ru[key], en[key] = pair
write(os.path.join(DATA, 'recipe/story/guiding_seed.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
      'ingredients': [{'tag': 'c:seeds'}, {'item': 'minecraft:redstone'}, {'tag': 'minecraft:coals'}],
      'result': {'id': 'expedition:guiding_seed', 'count': 2}})
# Gone with the one shared story and chapters opening by themselves: the Sprout, the chapter shards, the Great Shards.
for gone in ['branch_sprout', 'seal_shard'] + [seal for seal in SEALS]:
    for path in (os.path.join(DATA, 'recipe/story', gone + '.json'), os.path.join(ASSETS, 'models/item', gone + '.json'),
                 os.path.join(ASSETS, 'textures/item', gone + '.png')):
        if os.path.exists(path):
            os.remove(path)
write(os.path.join(ASSETS, 'models/item/guiding_seed.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'expedition:item/guiding_seed'}})

# --- route steps for the Twilight Forest and the Aether ---
for n, (step, (tier, structure, boss, dim, title, text, loot)) in enumerate(NEW_STEPS.items()):
    lo, hi = TIER_RING[tier]
    data = {'order': 100 + n, 'tier': tier, 'dimension': dim, 'min_distance': lo, 'max_distance': hi, 'structures': [structure], 'loot': loot}
    if boss:
        data['boss'] = boss
    write(os.path.join(DATA, 'expedition_route', step + '.json'), data)
    put(f'route.{step}.title', title)
    put(f'route.{step}.text', text)

# Every story dungeon's own land, as the Twilight Forest gives each of its bosses a biome: most fitting first. One is chosen
# per world (HomeBiomes), never one another dungeon has while a free one is left. Realm dungeons (Nether, Twilight, End,
# Aether) get theirs as a land of its own around the dungeon; Overworld dungeons are placed in one that lies there.
HOME_BIOMES = {
    '01_old_dungeon': ['terralith:moonlight_grove', 'minecraft:old_growth_pine_taiga', 'terralith:shield', 'minecraft:taiga', 'minecraft:forest'],
    '02_crypt': ['minecraft:dark_forest', 'minecraft:old_growth_spruce_taiga', 'minecraft:taiga', 'terralith:siberian_taiga'],
    '03_wrought_chamber': ['terralith:rocky_shrubland', 'terralith:cold_shrubland', 'terralith:steppe', 'minecraft:plains'],
    '04_nether_fortress': ['minecraft:nether_wastes', 'incendium:infernal_dunes'],
    '05_mossy_temple': ['minecraft:jungle', 'terralith:tropical_jungle', 'minecraft:sparse_jungle', 'terralith:rocky_jungle', 'terralith:jungle_mountains', 'minecraft:bamboo_jungle'],
    '06_underworld_arena': ['minecraft:soul_sand_valley', 'minecraft:nether_wastes'],
    '07_foundry': ['minecraft:badlands', 'terralith:savanna_badlands', 'terralith:hot_shrubland', 'terralith:rocky_shrubland'],
    '08_cursed_pyramid': ['minecraft:desert', 'terralith:ancient_sands', 'terralith:desert_canyon', 'terralith:desert_spires'],
    '09_frostbitten_temple': ['minecraft:ice_spikes', 'terralith:wintry_forest', 'minecraft:snowy_taiga', 'terralith:snowy_maple_forest', 'minecraft:snowy_plains'],
    '10_ocean_monument': ['terralith:deep_warm_ocean', 'minecraft:deep_lukewarm_ocean', 'minecraft:deep_ocean', 'minecraft:lukewarm_ocean', 'minecraft:ocean'],
    '11_pirate_ship': ['minecraft:frozen_ocean', 'minecraft:deep_frozen_ocean', 'minecraft:cold_ocean', 'minecraft:deep_cold_ocean', 'minecraft:ocean'],
    '12_kraken_ship': ['minecraft:deep_cold_ocean', 'minecraft:deep_ocean', 'minecraft:cold_ocean', 'minecraft:ocean', 'minecraft:deep_lukewarm_ocean'],
    '13_sky_whale': ['terralith:skylands_summer', 'terralith:skylands_spring', 'terralith:skylands_autumn', 'terralith:mirage_isles', 'minecraft:meadow'],
    '14_burning_arena': ['incendium:volcanic_deltas', 'minecraft:nether_wastes'],
    '15_soul_blacksmith': ['minecraft:crimson_forest', 'minecraft:nether_wastes'],
    '16_frosted_prison': ['minecraft:snowy_plains', 'terralith:snowy_shield', 'terralith:wintry_lowlands', 'terralith:ice_marsh', 'minecraft:ice_spikes'],
    '17_stronghold': ['terralith:steppe', 'terralith:shield_clearing', 'terralith:valley_clearing', 'minecraft:plains'],
    '18_ender_dragon': ['minecraft:end_highlands', 'minecraft:end_midlands'],
    '19_ruined_citadel': ['minecraft:end_midlands', 'minecraft:end_highlands'],
    '20_space_station': ['terralith:amethyst_canyon', 'terralith:amethyst_rainforest', 'terralith:white_mesa', 'terralith:bryce_canyon'],
    '21_ancient_factory': ['terralith:ashen_savanna', 'terralith:hot_shrubland', 'terralith:gravel_desert', 'minecraft:savanna'],
    '22_sunken_city': ['minecraft:deep_lukewarm_ocean', 'minecraft:deep_ocean', 'minecraft:deep_cold_ocean', 'minecraft:deep_frozen_ocean'],
    '23_acropolis': ['minecraft:warm_ocean', 'terralith:deep_warm_ocean', 'minecraft:lukewarm_ocean', 'minecraft:ocean', 'minecraft:deep_ocean'],
    '24_malkuth': ['minecraft:deep_ocean', 'terralith:deep_warm_ocean', 'minecraft:deep_cold_ocean'],
    '25_chesed': ['terralith:yellowstone', 'terralith:yosemite_lowlands', 'terralith:valley_clearing', 'terralith:lush_valley'],
    '26_geburah': ['nullscape:void_barrens', 'nullscape:shadowlands'],
    '27_lich_tower': ['minecraft:frozen_peaks', 'terralith:frozen_cliffs', 'minecraft:snowy_slopes', 'terralith:glacial_chasm'],
    '28_void_blossom': ['terralith:orchid_swamp', 'minecraft:mangrove_swamp', 'terralith:lush_valley', 'minecraft:swamp'],
    '29_nether_gauntlet': ['incendium:ash_barrens', 'incendium:toxic_heap'],
    '30_obsidilith': ['minecraft:end_barrens', 'minecraft:end_highlands'],
    '31_wither': ['incendium:quartz_flats'],
    '32_warped_nest': ['minecraft:warped_forest'],
    '33_umvuthana_grove': ['minecraft:savanna', 'terralith:ashen_savanna', 'terralith:fractured_savanna', 'minecraft:savanna_plateau', 'terralith:savanna_slopes'],
    '34_monastery': ['minecraft:jagged_peaks', 'terralith:emerald_peaks', 'terralith:haze_mountain', 'minecraft:stony_peaks'],
    '35_sandworm_nest': ['terralith:desert_spires', 'terralith:ancient_sands', 'terralith:gravel_desert', 'minecraft:desert'],
    '36_ruined_pyramid': ['terralith:desert_canyon', 'terralith:sandstone_valley', 'terralith:lush_desert', 'minecraft:desert'],
    '37_ancient_stronghold': ['minecraft:old_growth_spruce_taiga', 'minecraft:taiga', 'terralith:siberian_taiga', 'minecraft:dark_forest'],
    '38_lava_eater_spawn': ['minecraft:basalt_deltas', 'minecraft:nether_wastes'],
    '39_yeti_hideout': ['terralith:snowy_shield', 'minecraft:snowy_taiga', 'terralith:wintry_lowlands', 'minecraft:grove'],
    '40_frostmaw_den': ['terralith:snowy_badlands', 'minecraft:snowy_slopes', 'minecraft:ice_spikes', 'terralith:frozen_cliffs'],
    '41_skeletosaurus_nest': ['incendium:withered_forest', 'minecraft:soul_sand_valley'],
    '42_soul_fortress': ['incendium:weeping_valley', 'minecraft:soul_sand_valley'],
    '43_dragon_tower': ['terralith:volcanic_peaks', 'terralith:scarlet_mountains', 'minecraft:windswept_hills', 'terralith:rocky_mountains'],
    '44_shulker_tower': ['nullscape:crystal_peaks', 'minecraft:end_highlands'],
    '45_cloudy_temple': ['terralith:cloud_forest', 'terralith:haze_mountain', 'minecraft:meadow', 'minecraft:cherry_grove'],
    '46_collapsed_kingdom': ['minecraft:old_growth_birch_forest', 'minecraft:birch_forest', 'minecraft:flower_forest', 'minecraft:forest'],
    '47_lich_prison': ['minecraft:deep_frozen_ocean', 'minecraft:frozen_ocean', 'minecraft:deep_ocean'],
    '48_ancient_tower': ['nullscape:shadowlands', 'minecraft:end_midlands'],
    '49_naga_courtyard': ['twilightforest:forest', 'twilightforest:firefly_forest'],
    '50_tf_lich_tower': ['twilightforest:spooky_forest', 'twilightforest:enchanted_forest'],
    '51_labyrinth': ['twilightforest:swamp'],
    '52_hydra_lair': ['twilightforest:fire_swamp'],
    '53_knight_stronghold': ['twilightforest:dark_forest'],
    '54_dark_tower': ['twilightforest:dark_forest_center'],
    '55_yeti_cave': ['twilightforest:snowy_forest'],
    '56_aurora_palace': ['twilightforest:glacier'],
    '57_bronze_dungeon': ['aether:skyroot_meadow', 'aether:skyroot_woodland'],
    '58_silver_dungeon': ['deep_aether:sacred_lands', 'deep_aether:aerglow_forest'],
    '59_gold_dungeon': ['deep_aether:golden_heights', 'deep_aether:golden_grove'],
    '60_brass_dungeon': ['deep_aether:mystic_aerglow_forest', 'deep_aether:blue_aerglow_forest'],
    '61_ancient_temple': ['deeperdarker:echoing_forest', 'deeperdarker:overcast_columns', 'deeperdarker:deeplands'],
}

# --- every step's scenes (approach, fall) and text overrides; the speakers go into the step's data ---
for f in glob.glob(os.path.join(DATA, 'expedition_route', '*.json')):
    sid = os.path.basename(f)[:-5]
    d = json.load(open(f))
    arrive, fall = STEP_SCENES[sid]
    put(f'story.arrive.{sid}.1', arrive[1])
    if fall:
        put(f'story.fall.{sid}.1', fall[1])
    d['scenes'] = arrive[0] + '|' + (fall[0] if fall else '')
    d['home_biomes'] = HOME_BIOMES[sid]
    if sid in GUARDIANS:
        entity, health, damage, scale, armor, bar, name = GUARDIANS[sid][:7]
        d['guardian'] = {'entity': entity, 'health': health, 'damage': damage, 'scale': scale, 'armor': armor, 'bar': bar}
        if len(GUARDIANS[sid]) > 7:
            d['guardian']['rise'] = GUARDIANS[sid][7]
        if len(GUARDIANS[sid]) > 8:
            d['guardian']['water'] = GUARDIANS[sid][8]
        put(f'guardian.{sid}', name)
    else:
        d.pop('guardian', None)
    if sid in GARRISONS:
        d['garrison'] = GARRISONS[sid]
    else:
        d.pop('garrison', None)
    write(f, d)
    if sid in STEP_TEXT_OVERRIDES:
        put(f'route.{sid}.text', STEP_TEXT_OVERRIDES[sid])

# --- advancement tree ---
steps = []
for f in glob.glob(os.path.join(DATA, 'expedition_route', '*.json')):
    d = json.load(open(f)); d['_id'] = os.path.basename(f)[:-5]; steps.append(d)
steps.sort(key=lambda d: (int(d['tier'][1:]), d['order']))
ADV = os.path.join(DATA, 'advancement/story')
for old in glob.glob(os.path.join(ADV, '**/*.json'), recursive=True):
    os.remove(old)
def adv(path, parent, icon, title, desc, frame='task', criteria=None, background=None, toast=True, hidden=False):
    display = {'icon': {'id': icon}, 'title': {'translate': title}, 'description': {'translate': desc},
               'frame': frame, 'show_toast': toast, 'announce_to_chat': False, 'hidden': hidden}
    if background:
        display['background'] = background
    obj = {'display': display, 'criteria': criteria or {'done': {'trigger': 'minecraft:impossible'}}}
    if parent:
        obj['parent'] = 'expedition:story/' + parent
    write(os.path.join(ADV, path + '.json'), obj)
adv('root', None, 'minecraft:compass', 'expedition.advancement.story.root', 'expedition.advancement.story.root.desc',
    'task', {'done': {'trigger': 'minecraft:tick'}}, 'minecraft:textures/block/dark_oak_planks.png', toast=False)
previous = 'root'
for act in range(1, 8):
    keystones = ACT_DEFS[act]
    adv(f'act_{act}', previous, 'minecraft:filled_map', f'expedition.story.act.{act}.title', f'expedition.story.act.{act}.subtitle', 'goal')
    for d in steps:
        if int(d['tier'][1:]) + 1 != act:
            continue
        sid = d['_id']
        adv(f'step/{sid}', f'act_{act}', 'expedition:boss_heart' if d.get('boss') else 'minecraft:chest',
            f'expedition.route.{sid}.title', f'expedition.route.{sid}.text', 'challenge' if sid in keystones else 'task')
    previous = f'act_{act}'
adv('finale', 'act_7', 'minecraft:nether_star', 'expedition.advancement.story.finale', 'expedition.advancement.story.finale.desc', 'challenge')

# --- the guide book (Patchouli, i18n: texts are lang keys) ---
BOOKDIR = os.path.join(ASSETS, 'patchouli_books/guide/en_us')
for old in glob.glob(os.path.join(BOOKDIR, '**/*.json'), recursive=True):
    os.remove(old)
write(os.path.join(DATA, 'patchouli_books/guide/book.json'), {
    'name': 'expedition.guide.name', 'landing_text': 'expedition.guide.landing', 'i18n': True, 'use_resource_pack': True,
    'model': 'patchouli:book_brown', 'show_progress': False, 'version': 1})
put('guide.name', BOOK['name'])
put('guide.landing', BOOK['landing'])
for cat, (icon, name, desc, sort) in CATEGORIES.items():
    write(os.path.join(BOOKDIR, 'categories', cat + '.json'), {'name': f'expedition.guide.cat.{cat}', 'description': f'expedition.guide.cat.{cat}.desc',
                                                                'icon': icon, 'sortnum': sort})
    put(f'guide.cat.{cat}', name)
    put(f'guide.cat.{cat}.desc', desc)
def entry(eid, cat, icon, title, text, sort, lock):
    pages_ru, pages_en = text[0].split('\n\n'), text[1].split('\n\n')
    assert len(pages_ru) == len(pages_en), eid
    pages = []
    for i, (pr, pe) in enumerate(zip(pages_ru, pages_en)):
        put(f'guide.{eid}.page{i}', (pr, pe))
        pages.append({'type': 'patchouli:text', 'text': f'expedition.guide.{eid}.page{i}'})
    put(f'guide.{eid}', title)
    obj = {'name': f'expedition.guide.{eid}', 'icon': icon, 'category': f'expedition:{cat}', 'sortnum': sort, 'pages': pages}
    if lock:
        obj['advancement'] = lock
    write(os.path.join(BOOKDIR, 'entries', cat, eid + '.json'), obj)
for eid, (cat, icon, title, text, sort, lock) in ENTRIES.items():
    entry(eid, cat, icon, title, text, sort, lock)
for act, text in STORY_ENTRIES.items():
    entry(f'act_{act}', 'story', 'minecraft:filled_map', ACTS[act]['title'],
          (ACTS[act]['lines'][1][0] + '\n\n' + text[0], ACTS[act]['lines'][1][1] + '\n\n' + text[1]), act, f'expedition:story/act_{act}')
entry('finale', 'story', 'minecraft:nether_star', FINALE['title'], (FINALE['lines'][1][0] + '\n\n' + FINALE['lines'][3][0],
      FINALE['lines'][1][1] + '\n\n' + FINALE['lines'][3][1]), 99, 'expedition:story/finale')

# --- merge into the lang files ---
for lang, table in (('ru_ru', ru), ('en_us', en)):
    path = os.path.join(ASSETS, 'lang', lang + '.json')
    current = json.load(open(path))
    current.update(table)
    with open(path, 'w') as f:
        f.write(json.dumps(current, indent=2, ensure_ascii=False) + '\n')
print(f'story: {len(ru)} keys, {len(steps)} steps, {len(glob.glob(os.path.join(ADV, "**/*.json"), recursive=True))} advancements')
