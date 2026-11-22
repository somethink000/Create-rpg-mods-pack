# Full item catalog of the pack: every item (lang key item.<ns>.<id>) with a module guess, as JSON lines.
import zipfile, json, re, sys, os, collections
mods = sys.argv[1]
MODULES = [
    ('spawn_egg', r'spawn_egg$'),
    ('music', r'music_disc|disc_fragment'),
    ('ammo', r'(bullet|cartridge|shell|ammo|round|casing|primer|arrow|bolt|charge|missile|rocket|torpedo|grenade|magazine|drum$|propellant|powder_charge)'),
    ('armor', r'(helmet|chestplate|leggings|boots|_hat$|_mask$|crown|_armor$|_cap$|_hood$|_robe$|_tunic$|_pants$|_greaves$|_cuirass$)'),
    ('weapon', r'(sword|_axe$|bow$|crossbow|spear|trident|dagger|blade|hammer|mace|scythe|halberd|glaive|lance|sickle|claw|whip|katana|greatsword|cutlass|rapier|cannon|flail|knife|harpoon|blaster|gun|rifle|pistol|revolver|shotgun|gatling|launcher|flintlock|musket|javelin|club|anchor$|tesseract|splitter)'),
    ('shield', r'shield|buckler'),
    ('tool', r'(pickaxe|shovel|_hoe$|wrench|drill|saw$|shears|fishing_rod|_tool$|goggles|backtank|compass|map$|spyglass|lantern$|staff|wand|rod$|scepter|tome|book$)'),
    ('curio', r'(ring|amulet|necklace|belt|bracelet|glove|charm|talisman|totem|cloak|cape|pendant|relic|artifact|_eye$|gem$|_gem_|anklet|bottle_of|mitt|scarf|flippers|sneakers|pocket|horn)'),
    ('food', r'(bread|pie|cake|soup|stew|apple|berry|berries|meat|steak|chicken|pork|mutton|beef|fish|cookie|candy|chocolate|honey|juice|tea|coffee|milk|cheese|sandwich|burger|salad|pasta|rice|noodle|toast|cooked|roast|fried|baked|jam|syrup|sugar|_nut|nuts|carrot|potato|onion|tomato|cabbage|wine|beer|drink|cup|slice|dumpling|sausage|bacon|egg$|donut|waffle|pancake|muffin|cream|icecream|ice_cream|popsicle|bar$|bowl)'),
    ('consumable', r'(potion|flask|scroll|elixir|tonic|vial|bottle|syringe|bandage|medkit|fuel|canister|battery|key$|_key_|ticket|token|crystal$|orb$|essence|soul$|core$|heart$)'),
    ('part', r'(gear|cog|shaft|sheet|plate|rod|wire|spring|mechanism|circuit|tube|pipe|valve|propeller|rotor|engine|motor|chassis|frame|casing|component|module|blueprint|schematic|nozzle|turbine|pump|piston|bearing|coil|lens|chip|board|panel|ingot|nugget|dust|alloy|scrap|shard|fragment|chunk|crushed|raw_|gem|plating|mesh)'),
]
rows = []
for name in sorted(os.listdir(mods)):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    seen = set()
    for n in z.namelist():
        m = re.match(r'assets/([^/]+)/lang/en_us.json$', n)
        if not m: continue
        try: lang = json.loads(z.read(n))
        except Exception: continue
        for k, v in lang.items():
            mm = re.match(r'item\.([a-z0-9_]+)\.([a-z0-9_/]+)$', k)
            if not mm or mm.group(1) != m.group(1): continue
            iid = f'{mm.group(1)}:{mm.group(2)}'
            if iid in seen: continue
            seen.add(iid)
            mod = next((mo for mo, rx in MODULES if re.search(rx, mm.group(2))), 'other')
            rows.append(dict(id=iid, name=v, module=mod, jar=name))
with open(sys.argv[2], 'w') as f:
    for r in rows: f.write(json.dumps(r, ensure_ascii=False) + '\n')
c = collections.Counter(r['module'] for r in rows)
print(len(rows), c.most_common())
