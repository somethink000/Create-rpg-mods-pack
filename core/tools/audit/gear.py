# Equipment audit: per mod, gear-like items and whether the lang file gives them an ability description.
import zipfile, json, re, sys, os, collections
mods = sys.argv[1]
GEAR = re.compile(r'(sword|axe|pickaxe|shovel|hoe|bow|crossbow|spear|trident|dagger|blade|hammer|mace|scythe|staff|wand|gauntlet|katana|halberd|glaive|lance|sickle|claw|whip|shield|helmet|chestplate|leggings|boots|_hat|mask|crown|cape|cloak|ring|amulet|necklace|belt|bracelet|glove|charm|talisman|totem|armor|greatsword|cutlass|rapier|tome|book_of|scepter|launcher|cannon|flail|knife|harpoon|anchor|blaster|orb)')
DESC = re.compile(r'\.(desc|description|tooltip|lore|info|ability|effect)|tooltip\.|\.shift')
rows = collections.defaultdict(list)
for name in sorted(os.listdir(mods)):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        m = re.match(r'assets/([^/]+)/lang/en_us.json$', n)
        if not m: continue
        ns = m.group(1)
        try: lang = json.loads(z.read(n))
        except Exception: continue
        keys = list(lang)
        for k in keys:
            mm = re.match(rf'item\.{ns}\.([a-z0-9_]+)$', k)
            if not mm or not GEAR.search(mm.group(1)): continue
            item = mm.group(1)
            has = [x for x in keys if x != k and item in x and DESC.search(x)]
            rows[ns].append((item, lang[k], len(has)))
for ns, items in sorted(rows.items(), key=lambda kv: -len(kv[1])):
    feat = sum(1 for i in items if i[2] > 0)
    print(f'{ns:<28} gear={len(items):>4} with_desc={feat:>4}')
json.dump({k: v for k, v in rows.items()}, open(sys.argv[2], 'w'), ensure_ascii=False)
