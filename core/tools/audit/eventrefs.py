# For "check" gear items: does any gameplay class (not registry/client/datagen/compat) reference the item's registry field?
# Such a reference (event handler, mixin, ability system) means the item has an effect beyond its stats.
import zipfile, json, re, sys, os, collections
mods, verdicts_path, out_path = sys.argv[1:4]
rows = [json.loads(l) for l in open(verdicts_path)]
SKIP = re.compile(r'(client|render|model|datagen|data/|compat|jei|emi|rei|registry|registries|init/|/items?\.class$|Items\.class$|ModItems|ItemRegistry|Tab|creative|recipe|loot|tag|lang|Provider|integration|patchouli|advancement|trigger)', re.I)
by_jar = collections.defaultdict(list)
for r in rows:
    if r['verdict'] == 'stats?': by_jar[r['jar']].append(r)
for jar, items in by_jar.items():
    z = zipfile.ZipFile(os.path.join(mods, jar))
    classes = {n: z.read(n) for n in z.namelist() if n.endswith('.class') and not SKIP.search(n)}
    for it in items:
        path = it['id'].split(':')[1]
        field = path.upper().encode()
        hits = [n.split('/')[-1][:-6] for n, b in classes.items() if re.search(rb'(?<![A-Z_])' + re.escape(field) + rb'(?![A-Z_])', b)]
        if it.get('class'):
            own = it['class'].encode()
            hits = [h for h in hits if h != it['class']]
        it['refs'] = hits[:5]
        if hits: it['verdict'] = 'special'; it['why'] = 'эффект в событиях: ' + ', '.join(hits[:3])
with open(out_path, 'w') as f:
    for r in rows: f.write(json.dumps(r, ensure_ascii=False) + '\n')
c = collections.Counter(r['verdict'] for r in rows)
print(c)
for r in rows:
    if r.get('refs'): print(f"  {r['id']:<50} {', '.join(r['refs'][:3])}")
