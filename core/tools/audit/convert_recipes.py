# Converts gear recipes to Create: mechanical crafting with Create parts and a tier mechanism row, or (entry "mode": "table")
# a crafting-table recipe with Create sheets/shafts instead of ingots/sticks, or ("mode": "mixing") a superheated
# Create mixing of a shapeless recipe's ingredients. Entry "part" overrides the tier mechanism.
# Output: <res>/data/expedition/recipe/converted/<ns>/<path>.json; the mod drops the original recipe with that id.
# Usage: convert_recipes.py <recipes.json>... <resources dir>  (gear_recipes_full.json, create_gear_recipes.json)
import json, sys, os, shutil
*srcs, res = sys.argv[1:]
TIER_PART = {ns: 'create:precision_mechanism' for ns in ('mowziesmobs', 'legendary_monsters', 'graveyard')}
STEEL = 'tfmg:steel_mechanism'
SWAP = {'minecraft:iron_ingot': 'create:iron_sheet', 'c:ingots/iron': 'create:iron_sheet',
        'minecraft:gold_ingot': 'create:golden_sheet', 'c:ingots/gold': 'create:golden_sheet',
        'minecraft:copper_ingot': 'create:copper_sheet', 'c:ingots/copper': 'create:copper_sheet',
        'minecraft:stick': 'create:shaft', 'c:rods/wooden': 'create:shaft'}
def swap(ing):
    if isinstance(ing, dict):
        key = ing.get('item') or ing.get('tag')
        if key in SWAP:
            return {'item': SWAP[key]}
    return ing
def free_char(used):
    return next(c for c in 'PQZXWVUKJ#@' if c not in used)
out_dir = os.path.join(res, 'data/expedition/recipe/converted')
shutil.rmtree(out_dir, ignore_errors=True)
done, skipped = 0, []
for entry in [e for src in srcs for e in json.load(open(src))]:
    r, rid = entry['recipe'], entry['id']
    ns, path = rid.split(':', 1)
    t = r.get('type')
    if entry.get('mode') == 'mixing':
        assert t == 'minecraft:crafting_shapeless', rid
        result = r['result']
        recipe = {'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': 'create'}], 'type': 'create:mixing', 'heat_requirement': 'superheated', 'ingredients': r['ingredients'],
                  'results': [{'count': result.get('count', 1), 'id': result.get('id') or result.get('item')}]}
        target = os.path.join(out_dir, ns, path + '.json')
        os.makedirs(os.path.dirname(target), exist_ok=True)
        json.dump(recipe, open(target, 'w'), indent=2)
        done += 1
        continue
    if t == 'minecraft:crafting_shaped':
        pattern = list(r['pattern'])
        key = {k: swap(v) for k, v in r['key'].items()}
    elif t == 'minecraft:crafting_shapeless':
        ings = [swap(i) for i in r['ingredients']]
        chars = 'ABCDEFGHI'[:len(ings)]
        key = {c: i for c, i in zip(chars, ings)}
        pattern = [chars[i:i + 3] for i in range(0, len(chars), 3)]
    elif t == 'cataclysm:weapon_fusion':
        if 'elytra' in json.dumps(r):
            skipped.append(f'{rid} (gives flight: banned)')
            continue
        # Fusion altar → Mechanical Crafter: base + addition joined by a steel mechanism over a heavy plate.
        key = {'B': r['base'], 'A': r['addition'], 'H': {'item': 'tfmg:heavy_plate'}}
        pattern = ['B A', ' H ']
    elif t == 'minecraft:smithing_transform':
        key = {'T': r['template'], 'B': r['base'], 'A': swap(r['addition'])}
        pattern = ['TBA']
    else:
        skipped.append(f'{rid} ({t})')
        continue
    width = max(len(row) for row in pattern)
    pattern = [row.ljust(width) for row in pattern]
    table = entry.get('mode') == 'table'
    part = None if table else entry.get('part') or TIER_PART.get(entry['result'].split(':')[0], STEEL)
    if part:
        part_char = free_char(set(key))
        key[part_char] = {'item': part}
        pattern.append((part_char).center(width))
    result = r['result'] if isinstance(r['result'], dict) else {'id': r['result']}
    result = {'count': result.get('count', 1), 'id': result.get('id') or result.get('item')}
    mods = sorted(({'create', ns, entry['result'].split(':')[0]} | ({'tfmg'} if part == STEEL else set())) - {'minecraft'})
    recipe = {'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': m} for m in mods],
              'type': 'minecraft:crafting_shaped' if table else 'create:mechanical_crafting', 'category': 'equipment',
              'key': key, 'pattern': pattern, 'result': result}
    if not table:
        recipe.update({'accept_mirrored': False, 'show_notification': True})
    target = os.path.join(out_dir, ns, path + '.json')
    os.makedirs(os.path.dirname(target), exist_ok=True)
    json.dump(recipe, open(target, 'w'), indent=2)
    done += 1
print(done, 'converted; skipped (special machines):', *skipped, sep='\n  ')
