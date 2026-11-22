# For boss mods: which entity ids appear inside each structure template folder (NBT), to map bosses to structures.
import zipfile, gzip, re, sys, os, json, collections
mods = sys.argv[1]
targets = ['L_Ender', 'BOMD', 'fdbosses', 'block_factorys', 'legendary_monsters', 'mowziesmobs', 'aquamirae', 'sky_whale', 'graveyard', 'Incendium', 'deeperdarker', 'aether-1', 'deep_aether']
for name in sorted(os.listdir(mods)):
    if not any(t in name for t in targets): continue
    z = zipfile.ZipFile(os.path.join(mods, name))
    ents = set()
    for n in z.namelist():
        if re.match(r'assets/[^/]+/lang/en_us.json$', n):
            try:
                for k in json.loads(z.read(n)):
                    m = re.match(r'entity\.([a-z0-9_]+)\.([a-z0-9_]+)$', k)
                    if m: ents.add(f'{m.group(1)}:{m.group(2)}')
            except Exception: pass
    found = collections.defaultdict(set)
    for n in z.namelist():
        m = re.match(r'data/([^/]+)/structures?/(.+)\.nbt$', n)
        if not m: continue
        folder = m.group(2).split('/')[0]
        raw = z.read(n)
        try: raw = gzip.decompress(raw)
        except Exception: pass
        for e in set(re.findall(rb'([a-z0-9_]+:[a-z0-9_]+)', raw)):
            e = e.decode()
            if e in ents: found[folder].add(e.split(':')[1])
    print('==', name[:40])
    for f, es in sorted(found.items()):
        print(f'   {f:<32} {", ".join(sorted(es))[:150]}')
