# Item -> tags map from every jar's data/*/tags/item(s)/**.json (tag references expanded one level deep, recursively).
import zipfile, json, re, sys, os, collections
mods = sys.argv[1]
raw = collections.defaultdict(set)
for name in os.listdir(mods):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        m = re.match(r'data/([^/]+)/tags/items?/(.+)\.json$', n)
        if not m: continue
        try: vals = json.loads(z.read(n)).get('values', [])
        except Exception: continue
        for v in vals:
            raw[f'{m.group(1)}:{m.group(2)}'].add(v if isinstance(v, str) else v.get('id'))
def expand(tag, seen):
    out = set()
    for v in raw.get(tag, ()):
        if v is None: continue
        if v.startswith('#'):
            t = v[1:]
            if t not in seen:
                seen.add(t); out |= expand(t, seen)
        else: out.add(v)
    return out
item_tags = collections.defaultdict(list)
for tag in raw:
    for item in expand(tag, {tag}):
        item_tags[item].append(tag)
json.dump(item_tags, open(sys.argv[2], 'w'))
print(len(item_tags))
