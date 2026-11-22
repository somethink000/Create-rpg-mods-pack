#!/usr/bin/env python3
"""Flattens Jar-in-Jar: NeoForge keeps every nested jar of every mod wholly in the heap (three copies each, ~190 MB on
this pack), while a jar in the mods folder is read from disk. For each nested artifact one copy is kept - the one
NeoForge would pick (the newest) - written into the mods folder when it can stand there (a mod, or a jar marked
FMLModType), else left nested in its holder; the other copies are taken out of their holders (jar + metadata.json).
Artifacts the loader itself provides (MixinExtras) are taken out everywhere.

Usage: flatten_jarjar.py <mods dir> [--apply]   (dry run without --apply; originals go to <mods dir>/../mods-removed)
"""
import io, json, os, re, shutil, sys, zipfile

PROVIDED = {'mixinextras-neoforge'}  # NeoForge ships its own (0.5.5 here)
# Wrappers whose copy in the folder has no shared id with the nested one (Kotlin for Forge 5.12 "all" vs 5.3 "all").
IN_FOLDER = {'kotlinforforge-neoforge'}
mods = sys.argv[1]; apply = '--apply' in sys.argv
removed = os.path.join(mods, '..', 'mods-removed')


def ver(v):
    return [int(x) if x.isdigit() else x for x in re.split(r'[.\-+]', v)]


def newer(a, b):
    for x, y in zip(ver(a), ver(b)):
        if x != y:
            return (isinstance(x, int), x) > (isinstance(y, int), y) if type(x) == type(y) else isinstance(x, int)
    return len(ver(a)) > len(ver(b))


def ids(z):
    """Mod ids and module name a jar provides (what two copies of one thing share)."""
    out = set()
    names = set(z.namelist())
    manifest = z.read('META-INF/MANIFEST.MF') if 'META-INF/MANIFEST.MF' in names else b''
    # A LIBRARY / GAMELIBRARY jar's mods.toml is not read by the loader (Sodium's outer jar: the mod is nested in it).
    if 'META-INF/neoforge.mods.toml' in names and b'FMLModType' not in manifest:
        toml = z.read('META-INF/neoforge.mods.toml').decode('utf8', 'replace')
        for block in re.split(r'^\s*\[', toml, flags=re.M):
            if block.startswith('[mods]]'):
                out |= {'mod:' + m for m in re.findall(r'^\s*modId\s*=\s*"([^"]+)"', block, re.M)}
    if 'META-INF/MANIFEST.MF' in names:
        m = re.search(rb'Automatic-Module-Name:\s*(\S+)', z.read('META-INF/MANIFEST.MF'))
        if m:
            out.add('module:' + m.group(1).decode())
    return out


def standalone(data):
    z = zipfile.ZipFile(io.BytesIO(data))
    names = set(z.namelist())
    if 'META-INF/neoforge.mods.toml' in names:
        return True
    return 'META-INF/MANIFEST.MF' in names and b'FMLModType' in z.read('META-INF/MANIFEST.MF')


copies = {}  # artifact -> [(version, holder, path, entry)]
top = set()  # what the jars in the folder provide themselves
for name in sorted(os.listdir(mods)):
    if not name.endswith('.jar'):
        continue
    try:
        z = zipfile.ZipFile(os.path.join(mods, name))
        top |= ids(z)
        meta = json.loads(z.read('META-INF/jarjar/metadata.json'))
    except (KeyError, zipfile.BadZipFile):
        continue
    for e in meta['jars']:
        art = e['identifier']['group'] + ':' + e['identifier']['artifact']
        copies.setdefault(art, []).append((e['version']['artifactVersion'], name, e['path'], e))

# One artifact may be nested under several names (Flywheel as flywheel-neoforge-1.21.1 and as neoforge): copies are
# grouped by the mod (or module) they provide.
merged = {}
for art, cs in copies.items():
    inner = ids(zipfile.ZipFile(io.BytesIO(zipfile.ZipFile(os.path.join(mods, cs[0][1])).read(cs[0][2]))))
    key = sorted(i for i in inner if i.startswith('mod:'))[:1] or sorted(inner)[:1] or [art]
    merged.setdefault(key[0], (art, []))[1].extend(cs)
copies = dict(merged.values())

drop = {}  # holder -> set of nested paths to take out
extract = []  # (holder, path)
for art, cs in copies.items():
    if art.split(':')[1] in IN_FOLDER:
        for v, h, p, _ in cs:
            drop.setdefault(h, set()).add(p)
        print('drop all  %-55s (its newer copy is in the folder)' % art)
        continue
    if art.split(':')[1] in PROVIDED:
        for v, h, p, _ in cs:
            drop.setdefault(h, set()).add(p)
        print('drop all  %-55s (loader provides it)' % art)
        continue
    inner = ids(zipfile.ZipFile(io.BytesIO(zipfile.ZipFile(os.path.join(mods, cs[0][1])).read(cs[0][2]))))
    if inner & top:
        for v, h, p, _ in cs:
            drop.setdefault(h, set()).add(p)
        print('drop all  %-55s (a jar in the folder provides %s)' % (art, sorted(inner & top)[0]))
        continue
    best = cs[0]
    for c in cs[1:]:
        if newer(c[0], best[0]):
            best = c
    data = zipfile.ZipFile(os.path.join(mods, best[1])).read(best[2])
    holder = zipfile.ZipFile(os.path.join(mods, best[1]))
    service = b'FMLModType' in (holder.read('META-INF/MANIFEST.MF') if 'META-INF/MANIFEST.MF' in holder.namelist() else b'')
    # Out of a library holder (Sodium's service jar around the mod) nothing is moved: it may be loaded with it.
    move = standalone(data) and len(data) > 200_000 and not service
    for v, h, p, _ in cs:
        if (v, h, p) != best[:3] or move:
            drop.setdefault(h, set()).add(p)
    if move:
        extract.append((best[1], best[2]))
    if move or len(cs) > 1:
        print('%-9s %-55s keep %s from %s, drop %d other' % ('extract' if move else 'dedupe', art, best[0], best[1], len(cs) - 1))

if not apply:
    sys.exit(0)
os.makedirs(removed, exist_ok=True)
for holder, path in extract:
    out = os.path.join(mods, os.path.basename(path))
    with open(out, 'wb') as f:
        f.write(zipfile.ZipFile(os.path.join(mods, holder)).read(path))
for holder, paths in drop.items():
    src = os.path.join(mods, holder)
    if not os.path.exists(os.path.join(removed, holder)):  # keep the original from a first run
        shutil.copy2(src, os.path.join(removed, holder))
    zin = zipfile.ZipFile(os.path.join(removed, holder))
    meta = json.loads(zin.read('META-INF/jarjar/metadata.json'))
    meta['jars'] = [e for e in meta['jars'] if e['path'] not in paths]
    tmp = src + '.tmp'
    with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename in paths:
                continue
            if item.filename == 'META-INF/jarjar/metadata.json':
                if meta['jars']:
                    zout.writestr(item, json.dumps(meta, indent=2))
                continue
            zout.writestr(item, zin.read(item.filename))
    os.replace(tmp, src)
print('applied: %d extracted, %d holders rewritten' % (len(extract), len(drop)))
