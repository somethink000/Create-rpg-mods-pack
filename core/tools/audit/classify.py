# Reclassify the catalog: item tags first (what mods declare), name patterns as fallback.
import json, re, sys, collections
cat = [json.loads(l) for l in open(sys.argv[1])]
tags = json.load(open(sys.argv[2]))
TAG_RULES = [
    ('spawn_egg', r'spawn_egg'),
    ('shield', r'(^c:tools/shield|shields?$)'),
    ('armor', r'(enchantable/(armor|head_armor|chest_armor|leg_armor|foot_armor|equippable)|^c:armors|trimmable_armor|^minecraft:(head|chest|leg|foot)_armor)'),
    ('weapon', r'(^minecraft:swords|enchantable/(sword|weapon|bow|crossbow|trident|mace|sharp_weapon|fire_aspect)|^c:tools/(melee_weapon|ranged_weapon|bow|crossbow|spear|trident|mace)|^c:(swords|bows|crossbows|tridents))'),
    ('tool', r'(^minecraft:(pickaxes|shovels|hoes|axes)|enchantable/(mining|fishing)|^c:tools|^c:(pickaxes|shovels|hoes|axes|shears|fishing_rods))'),
    ('curio', r'^(curios|accessories|artifacts|relics):'),
    ('food', r'^c:foods'),
    ('ammo', r'(ammo|bullet|shell|cartridge|munition|propellant)'),
    ('material', r'^c:(ingots|nuggets|gems|dusts|plates|rods|raw_materials|wires|gears|sheets)'),
]
def classify(r):
    ts = tags.get(r['id'], [])
    for mod, rx in TAG_RULES:
        if any(re.search(rx, t) for t in ts): return mod, 'tag'
    return r['module'], 'name'
out = collections.Counter(); bymod = collections.defaultdict(collections.Counter)
for r in cat:
    r['module'], r['how'] = classify(r)
    out[r['module']] += 1
    bymod[r['id'].split(':')[0]][r['module']] += 1
with open(sys.argv[3], 'w') as f:
    for r in cat: f.write(json.dumps(r, ensure_ascii=False) + '\n')
print(out.most_common())
mods = sorted(bymod.items(), key=lambda kv: -sum(kv[1][m] for m in ('weapon','armor','tool','shield','curio')))
print(f"{'mod':<26}{'всего':>6}{'оруж':>6}{'брон':>6}{'инстр':>6}{'щит':>5}{'бижут':>6}{'еда':>5}{'расх':>5}{'патр':>5}{'мат':>5}{'деталь':>7}{'прочее':>7}")
for ns, c in mods[:45]:
    print(f"{ns:<26}{sum(c.values()):>6}{c['weapon']:>6}{c['armor']:>6}{c['tool']:>6}{c['shield']:>5}{c['curio']:>6}{c['food']:>5}{c['consumable']:>5}{c['ammo']:>5}{c['material']:>5}{c['part']:>7}{c['other']:>7}")
