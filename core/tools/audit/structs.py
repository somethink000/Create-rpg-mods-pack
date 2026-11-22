# Inventory worldgen structures (+ structure_set spacing and monster spawn overrides) across mod jars, JSON lines out.
import json, sys, zipfile, os, re
mods = sys.argv[1]
out = []
for name in sorted(os.listdir(mods)):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    names = z.namelist()
    sets = {}
    for n in names:
        m = re.match(r'data/([^/]+)/worldgen/structure_set/(.+)\.json$', n)
        if not m: continue
        try: d = json.loads(z.read(n))
        except Exception: continue
        pl = d.get('placement', {})
        for s in d.get('structures', []):
            sid = s.get('structure') if isinstance(s, dict) else s
            sets[sid] = (pl.get('spacing'), pl.get('separation'), pl.get('type','').split(':')[-1])
    for n in names:
        m = re.match(r'data/([^/]+)/worldgen/structure/(.+)\.json$', n)
        if not m: continue
        sid = f'{m.group(1)}:{m.group(2)}'
        try: d = json.loads(z.read(n))
        except Exception: d = {}
        mobs = []
        so = d.get('spawn_overrides', {}).get('monster', {}).get('spawns', [])
        for e in so: mobs.append(e.get('type'))
        biomes = d.get('biomes')
        out.append(dict(jar=name, id=sid, type=str(d.get('type','')).split(':')[-1], biomes=biomes if isinstance(biomes,str) else (biomes[:2] if isinstance(biomes,list) else None), spacing=sets.get(sid), mobs=mobs))
for o in out: print(json.dumps(o, ensure_ascii=False))
