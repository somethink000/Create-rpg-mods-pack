# Ties the pack's plain-crafted tech to Create (a "full pass" over every item a recipe makes, see /expedition audit recipes):
# vanilla redstone, workstations and tools, Advanced Peripherals' blocks, Gunsmithing's tools, Aeroworks' control modules.
# Their table recipes take Create sheets and shafts instead of ingots and sticks; the costly ones (beacon, conduit...)
# are made on a mechanical crafter with a precision mechanism, computer peripherals with an electron tube.
# Decoration, food, boats and the mods' own-world items (Aether, Twilight, dungeon mods) keep their recipes.
# Usage: gen_create_tech.py <recipe_audit.tsv> <mods dir> <vanilla server jar>  ->  tech_recipes.json
# then:  convert_recipes.py gear_recipes_full.json create_gear_recipes.json tech_recipes.json <resources dir>
import csv, glob, json, os, re, sys, zipfile
audit, mods, vanilla = sys.argv[1:4]
here = os.path.dirname(os.path.abspath(__file__))
TARGET = ('minecraft', 'advancedperipherals', 'cgs', 'aeroworks')
DECO = re.compile(r'(stairs|slab|_wall$|bricks?|planks|fence|door$|button|sign|carpet|pane|tiles?|pillar|polished|chiseled|smooth|cut_|mossy|cracked|_log$|_wood$|'
                  r'banner|bed$|candle|terracotta|concrete|wool|glass|lantern|torch|boat|raft|dye|_block$|bookshelf|scaffold|ladder|_on_a_stick|armor_stand|item_frame|painting|'
                  r'decorated_pot|flower_pot|banner_pattern|firework|map$|book|paper|bowl|stick$|sword|pickaxe|_axe$|shovel|hoe|helmet|chestplate|leggings|boots|shield|bow$)')
# Everyday things stay cheap: arrows, a campfire, a lever, rails (Steam 'n' Rails is the railway).
KEEP = {'minecraft:arrow', 'minecraft:campfire', 'minecraft:soul_campfire', 'minecraft:lever', 'minecraft:brush', 'minecraft:golden_apple',
        'minecraft:rail', 'minecraft:powered_rail', 'minecraft:detector_rail', 'minecraft:activator_rail', 'minecraft:tripwire_hook'}
COSTLY = {'minecraft:beacon', 'minecraft:conduit', 'minecraft:respawn_anchor', 'minecraft:lodestone', 'minecraft:end_crystal', 'minecraft:recovery_compass'}
SWAPPABLE = {'minecraft:iron_ingot', 'c:ingots/iron', 'minecraft:gold_ingot', 'c:ingots/gold', 'minecraft:copper_ingot', 'c:ingots/copper', 'minecraft:stick', 'c:rods/wooden'}
removed = {v['id'] if isinstance(v, dict) else v for v in json.load(open(os.path.join(here, '../../src/main/resources/data/expedition/tags/item/removed.json')))['values']}
wanted = set()
for row in csv.DictReader(open(audit), delimiter='\t'):
    ns, path = row['item'].split(':')
    if row['link'] == 'plain' and ns in TARGET and not DECO.search(path) and row['item'] not in removed and row['item'] not in KEEP:
        wanted.add(row['item'])
def keys(recipe):
    text = json.dumps(recipe)
    return set(re.findall(r'"(?:item|tag)":\s*"([^"]+)"', text))
found = []
for jar in sorted(glob.glob(os.path.join(mods, '*.jar'))) + [vanilla]:
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
            if not isinstance(item, str) or item not in wanted or recipe.get('type') not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless'):
                continue
            rid = f"{name.split('/')[1]}:{name.split('/recipe/')[1][:-5]}"
            if item in COSTLY:
                found.append({'id': rid, 'result': item, 'recipe': recipe, 'part': 'create:precision_mechanism'})
            elif item.startswith('advancedperipherals:'):
                found.append({'id': rid, 'result': item, 'recipe': recipe, 'part': 'create:electron_tube'})
            elif keys(recipe) & SWAPPABLE:
                found.append({'id': rid, 'result': item, 'recipe': recipe, 'mode': 'table'})
json.dump(sorted(found, key=lambda e: e['id']), open(os.path.join(here, 'tech_recipes.json'), 'w'), indent=1)
print(len(wanted), 'plain tech items;', len(found), 'recipes tied to Create:', ' '.join(sorted({e['result'].split(':')[1] for e in found})))
