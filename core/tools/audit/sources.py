# Where does each item of a module come from now: chest loot tables, entity (mob/boss) loot tables, recipes.
import zipfile, json, re, sys, os, collections
mods, catalog_path, module, out_path = sys.argv[1:5]
items = {json.loads(l)['id']: json.loads(l) for l in open(catalog_path) if json.loads(l)['module'] == module}
src = collections.defaultdict(lambda: {'chest': set(), 'entity': set(), 'recipe': set(), 'other': set()})
for name in os.listdir(mods):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        if not n.endswith('.json') or not n.startswith('data/'): continue
        kind = 'recipe' if re.search(r'/recipes?/', n) else ('chest' if re.search(r'/loot_tables?/.*(chest|treasure|reward|vault|archaeology|barrel)', n) else ('entity' if re.search(r'/loot_tables?/entit', n) else ('other' if re.search(r'/(loot_tables?|loot_modifiers)/', n) else None)))
        if not kind: continue
        try: text = z.read(n).decode('utf-8', 'ignore')
        except Exception: continue
        for iid in set(re.findall(r'"([a-z0-9_]+:[a-z0-9_/]+)"', text)):
            if iid in items:
                if kind == 'recipe':
                    try:
                        d = json.loads(text); res = json.dumps(d.get('result') or d.get('output') or d.get('results') or '')
                        if iid not in res: continue
                    except Exception: continue
                src[iid][kind].add(re.sub(r'^data/|\.json$', '', n))
rows = []
for iid, it in items.items():
    s = src.get(iid, {'chest': set(), 'entity': set(), 'recipe': set(), 'other': set()})
    rows.append(dict(id=iid, name=it['name'], chest=sorted(s['chest'])[:6], entity=sorted(s['entity'])[:6], recipe=sorted(s['recipe'])[:3], other=sorted(s['other'])[:4]))
json.dump(rows, open(out_path, 'w'), ensure_ascii=False)
c = collections.Counter(('chest ' if r['chest'] else '') + ('mob ' if r['entity'] else '') + ('craft ' if r['recipe'] else '') + ('modifier ' if r['other'] else '') or 'none' for r in rows)
print(len(rows), c.most_common())
