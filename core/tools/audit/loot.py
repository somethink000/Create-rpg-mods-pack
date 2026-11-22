# Chest loot tables across jars: expected count per opening of valuable items (rough: pool rolls x entry weight share x avg count).
import zipfile, json, re, sys, os, collections
mods = sys.argv[1]
VALUABLE = re.compile(r'(iron_ingot|iron_block|gold_ingot|gold_block|diamond|emerald|netherite|ancient_debris|copper_ingot|brass|zinc|steel|enchanted_golden_apple|golden_apple|totem|elytra|artifacts:|relics:|apotheosis:|_gem|andesite_alloy|precision_mechanism|blaze_rod)')
def num(v, default=1.0):
    if isinstance(v, (int, float)): return float(v)
    if isinstance(v, dict):
        if 'min' in v and 'max' in v: return (num(v['min']) + num(v['max'])) / 2
        if 'n' in v and 'p' in v: return num(v['n']) * num(v['p'])
        if 'value' in v: return num(v['value'])
    return default
def entry_count(e):
    c = 1.0
    for f in e.get('functions', []):
        if f.get('function', '').endswith('set_count'): c = num(f.get('count'), 1)
    return c
rows = []
for name in sorted(os.listdir(mods)):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        m = re.match(r'data/([^/]+)/loot_tables?/(chests?/.+|.*chest.*)\.json$', n)
        if not m: continue
        try: d = json.loads(z.read(n))
        except Exception: continue
        got = collections.Counter()
        for pool in d.get('pools', []):
            rolls = num(pool.get('rolls', 1)) + num(pool.get('bonus_rolls', 0), 0) * 0
            entries = [e for e in pool.get('entries', []) if e.get('type', '').endswith('item')]
            allw = sum(num(e.get('weight', 1)) for e in pool.get('entries', [])) or 1
            for e in entries:
                item = e.get('name', '')
                if VALUABLE.search(item):
                    got[item] += rolls * num(e.get('weight', 1)) / allw * entry_count(e)
        rows.append((f'{m.group(1)}:{m.group(2)}', name[:30], got))
rows.sort(key=lambda r: -sum(r[2].values()))
for t, j, got in rows:
    top = ', '.join(f'{k.split(":")[-1]}×{v:.1f}' for k, v in got.most_common(6))
    print(f'{sum(got.values()):6.1f}  {t:<66} {top}')
