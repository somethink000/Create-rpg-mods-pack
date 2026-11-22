# For each kept (special) gear item: its current recipes in all jars and the non-vanilla ingredients (= mod materials).
import zipfile, json, re, sys, os, collections
mods, gear_path, out_path = sys.argv[1:4]
gear = {json.loads(l)['id']: json.loads(l) for l in open(gear_path)}
keep = {k for k, v in gear.items() if v['verdict'] in ('special', 'stats?')}
def items_in(x, acc):
    if isinstance(x, dict):
        for k, v in x.items():
            if k in ('item', 'id') and isinstance(v, str) and ':' in v: acc.add(v)
            elif k == 'tag' and isinstance(v, str): acc.add('#' + v)
            else: items_in(v, acc)
    elif isinstance(x, list):
        for v in x: items_in(v, acc)
    return acc
recipes = collections.defaultdict(list)
for name in os.listdir(mods):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        if not re.match(r'data/[^/]+/recipes?/.+\.json$', n): continue
        try: d = json.loads(z.read(n))
        except Exception: continue
        res = d.get('result') or d.get('output') or d.get('results')
        outs = items_in(res, set()) if res is not None else set()
        for o in outs & keep:
            ins = items_in({k: v for k, v in d.items() if k not in ('result', 'output', 'results')}, set())
            recipes[o].append({'type': d.get('type', '?'), 'in': sorted(i for i in ins if not i.startswith(('minecraft:', '#minecraft:', '#c:')) and i != o), 'file': n})
json.dump(recipes, open(out_path, 'w'), ensure_ascii=False)
no = [k for k in keep if k not in recipes]
print('kept gear:', len(keep), 'with recipes:', len(recipes), 'without (drop/loot only):', len(no))
mats = collections.Counter(i for rs in recipes.values() for r in rs for i in r['in'])
print('top mod materials:'); print(', '.join(f'{k}×{v}' for k, v in mats.most_common(60)))
