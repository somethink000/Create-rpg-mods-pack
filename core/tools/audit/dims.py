# Resolve each boss/major structure's biome tag to a dimension guess (overworld/nether/end/other) from tag JSONs in all jars.
import zipfile, json, re, sys, os
mods, structs = sys.argv[1], sys.argv[2]
tags = {}
for name in os.listdir(mods):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        m = re.match(r'data/([^/]+)/tags/worldgen/biome/(.+)\.json$', n)
        if m:
            try: vals = json.loads(z.read(n)).get('values', [])
            except Exception: continue
            key = f'{m.group(1)}:{m.group(2)}'
            tags.setdefault(key, []).extend(v if isinstance(v, str) else v.get('id') for v in vals)
NETHER = ('nether', 'crimson', 'warped', 'basalt', 'soul_sand', 'incendium')
END = ('the_end', 'end_', 'nullscape', 'small_end')
def resolve(ref, seen=None):
    seen = seen or set()
    if ref is None: return set()
    if isinstance(ref, list): return set().union(*[resolve(r, seen) for r in ref]) if ref else set()
    if ref.startswith('#'):
        t = ref[1:]
        if t in seen: return set()
        seen.add(t)
        if t in ('minecraft:is_overworld',): return {'overworld'}
        if t == 'minecraft:is_nether': return {'nether'}
        if t == 'minecraft:is_end': return {'end'}
        return set().union(*[resolve(v, seen) for v in tags.get(t, [])]) if tags.get(t) else {('?'+t)}
    b = ref.split(':')[1]
    if any(k in ref for k in END): return {'end'}
    if any(k in b for k in NETHER): return {'nether'}
    if ref.startswith('twilightforest:'): return {'twilight'}
    if ref.startswith(('aether:', 'deep_aether:')): return {'aether'}
    if ref.startswith('deeperdarker:'): return {'otherside'}
    return {'overworld'}
want = sys.argv[3].split(',')
for l in open(structs):
    o = json.loads(l)
    if o['id'].split(':')[0] not in want: continue
    full = None
    for name in os.listdir(mods):
        pass
    print(f"{o['id']:<48} {','.join(sorted(resolve(o['biomes'] if isinstance(o['biomes'], str) else o['biomes'])))}")
