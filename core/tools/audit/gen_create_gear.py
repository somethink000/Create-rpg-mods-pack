# Ties the basic gear ladder and leftover table-crafted gear to Create, and gives uncraftable dungeon gear a recipe.
#  1. create_gear_recipes.json: the source recipes of CONVERT, with a tier rule each, for convert_recipes.py
#     (netherite ingot -> superheated Create mixing)
#     (iron/gold -> crafting table with Create sheets; diamond -> mechanical crafter + precision mechanism;
#      netherite and its peers -> mechanical crafter + TFMG steel mechanism).
#  2. <res>/data/expedition/recipe/create_gear/*.json: dungeon gear that has no recipe and never lies in chests
#     (made from the route boss's heart, or from the dungeon's key / a base item + trophies), and Garnished's
#     Create-metal hatchets (their own recipes need a mod we don't have).
# Usage: gen_create_gear.py <mods dir> <vanilla server jar> <resources dir>; then
#        convert_recipes.py gear_recipes_full.json create_gear_recipes.json <resources dir>
import glob, json, os, shutil, sys, zipfile
mods, vanilla, res = sys.argv[1:4]
here = os.path.dirname(os.path.abspath(__file__))

PRECISION, STEEL = 'create:precision_mechanism', 'tfmg:steel_mechanism'
STEEL_WORDS = ('netherite', 'echo', 'warden', 'stratus', 'stormforged', 'gravitite')
CONVERT = [f'minecraft:{m}_{p}' for m in ('iron', 'golden', 'diamond', 'netherite')
           for p in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots')] + [
    'minecraft:shield', 'minecraft:bow', 'minecraft:crossbow', 'minecraft:netherite_ingot',
    *[f'aquamirae:{w}' for w in ('terrible_blade', 'terrible_cleaver', 'terrible_fang', 'shatterblade', 'shatterspike', 'remnant_saber')],
    'create_deep_dark:echo_sword', *[f'create_deep_dark:echo_armor_{p}' for p in ('helmet', 'chestplate', 'leggings', 'boots')],
    *[f'aether:gravitite_{p}' for p in ('helmet', 'chestplate', 'leggings', 'boots')],
    *[f'deep_aether:{m}_{p}' for m in ('stratus', 'stormforged') for p in ('helmet', 'chestplate', 'leggings', 'boots')],
    *[f'farmersdelight:{m}_knife' for m in ('iron', 'golden', 'diamond', 'netherite')], 'farmersdelight:skillet']
CONVERTIBLE = ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'minecraft:smithing_transform')

def rule(item):
    path = item.split(':')[1]
    if item == 'minecraft:netherite_ingot':
        return {'mode': 'mixing'}  # gold + scrap alloyed in a superheated Create mixer, not on a table
    if any(word in path for word in STEEL_WORDS):
        return {'part': STEEL}
    if 'diamond' in path or item.startswith('aquamirae:'):
        return {'part': PRECISION}
    return {'mode': 'table'}

# 1. Source recipes from the mod jars and vanilla.
wanted, found = set(CONVERT), []
for jar in sorted(glob.glob(os.path.join(mods, '*.jar'))) + [vanilla]:
    if os.path.basename(jar).startswith('expedition-'):
        continue  # our own jar holds the converted copies
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if '/recipe/' not in name or not name.endswith('.json') or not name.startswith('data/'):
                continue
            try:
                recipe = json.loads(z.read(name))
            except ValueError:
                continue
            result = recipe.get('result')
            item = (result.get('id') or result.get('item')) if isinstance(result, dict) else result
            if isinstance(item, str) and item in wanted and recipe.get('type') in CONVERTIBLE:
                rid = f"{name.split('/')[1]}:{name.split('/recipe/')[1][:-5]}"
                if item == 'minecraft:netherite_ingot' and rid != item:
                    continue  # only scrap + gold moves to the mixer; block and nugget unpacking stay
                found.append({'id': rid, 'result': item, 'recipe': recipe, **rule(item)})
missing = wanted - {e['result'] for e in found}
assert not missing, f'no source recipe for {sorted(missing)}'
json.dump(sorted(found, key=lambda e: e['id']), open(os.path.join(here, 'create_gear_recipes.json'), 'w'), indent=1)

# 2. New recipes.
out = os.path.join(res, 'data/expedition/recipe/create_gear')
shutil.rmtree(out, ignore_errors=True)
os.makedirs(out)
FABRIC, SHARD = 'apotheosis:timeworn_fabric', 'apotheosis:luminous_crystal_shard'
TIER = {1: ('create:brass_sheet', PRECISION, FABRIC), 2: ('tfmg:heavy_plate', STEEL, SHARD), 3: ('tfmg:heavy_plate', STEEL, SHARD)}

def ing(x):
    return x if isinstance(x, dict) else {'tag': x[1:]} if x.startswith('#') else {'item': x}

def write(name, item, pattern, key, crafter=True):
    mods_needed = sorted({'create', item.split(':')[0]} | {v.split(':')[0] for v in key.values() if isinstance(v, str)} - {'minecraft'})
    recipe = {'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': m.lstrip('#')} for m in mods_needed if m not in ('c', '#c')],
              'type': 'create:mechanical_crafting' if crafter else 'minecraft:crafting_shaped', 'category': 'equipment',
              'pattern': pattern, 'key': {k: ing(v) for k, v in key.items()}, 'result': {'id': item, 'count': 1}}
    if crafter:
        recipe.update({'accept_mirrored': False, 'show_notification': True})
    json.dump(recipe, open(os.path.join(out, name + '.json'), 'w'), indent=2)

def heart(boss):
    return {'type': 'neoforge:components', 'items': 'expedition:boss_heart', 'components': {'expedition:boss': boss}}

# Boss gear of route bosses whose heart isn't spent yet: the heart crafts it (tier = the boss's route tier).
HEART = {**{f'aquamirae:{w}': ('aquamirae:captain_cornelia', 1) for w in ('tidepiercer', 'jawbreaker', 'tempest_trident', 'dagger_of_greed')},
         'block_factorys_bosses:pirate_saber': ('block_factorys_bosses:kraken', 3),
         **{f'block_factorys_bosses:{w}': ('block_factorys_bosses:underworld_knight', 2) for w in ('large_sword', 'warrior_sword', 'dagger')},
         'mowziesmobs:earthrend_gauntlet': ('mowziesmobs:sculptor', 2),
         # Materials of boss gear that nothing in these mods' versions drops (obtainable audit): from the boss's heart.
         'legendary_monsters:primal_ice_shard': ('legendary_monsters:frostbitten_golem', 2),
         'legendary_monsters:withered_horn': ('legendary_monsters:withered_abomination', 2),
         'aquamirae:maelstrom_vestige': ('aquamirae:captain_cornelia', 1)}
for item, (boss, tier) in HEART.items():
    sheet, part, _ = TIER[tier]
    write(item.replace(':', '_'), item, [' H ', 'SPS', ' S '], {'H': heart(boss), 'S': sheet, 'P': part})

# Dungeon loot gear: the dungeon's key (Aether bosses drop them) or a base item, its mobs' trophy and a rarity material.
A = lambda *names: [f'aether:{n}' for n in names]
ARMOR = ('helmet', 'chestplate', 'leggings', 'boots')
KEYED = {**{i: ('aether:bronze_dungeon_key', 'feral_fang', 1) for i in A('hammer_of_kingbdogz', 'pig_slayer', 'sentry_boots', *[f'neptune_{p}' for p in ARMOR])},
         **{i: ('aether:silver_dungeon_key', 'feral_fang', 2) for i in A('valkyrie_pickaxe', 'valkyrie_axe', 'valkyrie_shovel', 'valkyrie_lance',
            'flaming_sword', 'lightning_sword', 'holy_sword', 'vampire_blade', *[f'valkyrie_{p}' for p in ARMOR])},
         **{i: ('aether:gold_dungeon_key', 'cinder_core', 2) for i in A(*[f'phoenix_{p}' for p in ARMOR])},
         'cataclysm:coral_spear': ('minecraft:prismarine_crystals', 'abyssal_scale', 1), 'cataclysm:coral_bardiche': ('minecraft:prismarine_crystals', 'abyssal_scale', 1),
         'cataclysm:athame': ('minecraft:gold_ingot', 'grave_dust', 1), 'cataclysm:khopesh': ('minecraft:gold_ingot', 'grave_dust', 1),
         'twilightforest:gold_minotaur_axe': ('minecraft:golden_axe', 'feral_fang', 1),
         'twilightforest:mazebreaker_pickaxe': ('minecraft:diamond_pickaxe', 'feral_fang', 2), 'twilightforest:ice_sword': ('minecraft:blue_ice', 'grave_dust', 2),
         # Reward-chest gear and code-given parts no source gives in this pack (obtainable audit): from their dungeon's key.
         'aether:phoenix_bow': ('aether:bronze_dungeon_key', 'feral_fang', 1), 'aether:valkyrie_hoe': ('aether:silver_dungeon_key', 'feral_fang', 2),
         'deep_aether:blade_of_luck': ('deep_aether:brass_dungeon_key', 'raider_insignia', 2),
         'deep_aether:stormforged_smithing_template': ('deep_aether:brass_dungeon_key', 'void_shard', 2),
         'deep_aether:stratus_smithing_template': ('aether:enchanted_gravitite', 'void_shard', 2),
         'deep_aether:sun_core': ('aether:gold_dungeon_key', 'cinder_core', 2)}
# (Elytra-like gear stays out: player flight is banned in this pack.)
for item, (base, trophy, tier) in KEYED.items():
    sheet, part, material = TIER[tier]
    write(item.replace(':', '_'), item, ['MTM', 'SBS', ' P '], {'M': material, 'T': f'expedition:{trophy}', 'S': sheet, 'B': base, 'P': part})
# Obsidian armor is phoenix armor cooled: the phoenix piece in obsidian.
for p in ARMOR:
    write(f'aether_obsidian_{p}', f'aether:obsidian_{p}', ['OOO', 'OBO', ' P '], {'O': 'minecraft:obsidian', 'B': f'aether:phoenix_{p}', 'P': STEEL})

print(len(found), 'recipes to convert;', len(os.listdir(out)), 'new gear recipes')
