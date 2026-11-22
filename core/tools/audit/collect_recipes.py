# Full recipe JSON (with id) for every kept special gear item of the boss/dimension mods.
import zipfile, json, re, sys, os, collections
mods, gear_path, out = sys.argv[1:4]
TARGET = {'aether','deep_aether','twilightforest','legendary_monsters','cataclysm','mowziesmobs','block_factorys_bosses','aquamirae','deeperdarker','galosphere','graveyard','bosses_of_mass_destruction','fdbosses'}
keep = {json.loads(l)['id'] for l in open(gear_path) if json.loads(l)['verdict'] in ('special','stats?') and json.loads(l)['id'].split(':')[0] in TARGET}
def result_id(d):
    r = d.get('result') or d.get('output')
    if isinstance(r, dict): return r.get('id') or r.get('item')
    if isinstance(r, str): return r
    return None
found = []
for name in os.listdir(mods):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        m = re.match(r'data/([^/]+)/recipes?/(.+)\.json$', n)
        if not m: continue
        try: d = json.loads(z.read(n))
        except Exception: continue
        rid = result_id(d)
        if rid in keep:
            found.append({'id': f'{m.group(1)}:{m.group(2)}', 'result': rid, 'recipe': d})
json.dump(found, open(out, 'w'))
print(len(found), 'recipes for', len({f['result'] for f in found}), 'items of', len(keep))
print(collections.Counter(f['recipe'].get('type') for f in found).most_common())
