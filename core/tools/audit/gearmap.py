# Combat module: map each gear item to its class (by name) and hooks; no own class = standard vanilla class = stats only.
import zipfile, subprocess, json, re, sys, os, collections
mods, catalog_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
HOOKS = ['use', 'useOn', 'hurtEnemy', 'postHurtEnemy', 'inventoryTick', 'releaseUsing', 'onUseTick', 'finishUsingItem',
         'onLeftClickEntity', 'onEntitySwing', 'onArmorTick', 'canElytraFly', 'interactLivingEntity', 'curioTick', 'onEquip',
         'onUnequip', 'mineBlock', 'onCraftedBy', 'getUseDuration', 'appendHoverText']
GEAR = {'weapon', 'armor', 'shield', 'tool'}
cat = [json.loads(l) for l in open(catalog_path)]
by_jar = collections.defaultdict(list)
for r in cat:
    if r['module'] in GEAR: by_jar[r['jar']].append(r)
def snake(name):
    name = re.sub(r'(Item|Items)$', '', name)
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower().replace('__', '_')
results = []
for jar, items in by_jar.items():
    path = os.path.join(mods, jar)
    z = zipfile.ZipFile(path)
    classes = [n[:-6].replace('/', '.') for n in z.namelist() if n.endswith('.class') and '$' not in n]
    cand = [c for c in classes if re.search(r'(Item|Sword|Axe|Armor|Shield|Bow|Weapon|Helmet|Chestplate|Boots|Leggings|Hammer|Blade|Staff|Wand|Spear|Scythe|Gauntlet|Pickaxe|Tool|Cannon|Launcher|Gun)$', c.split('.')[-1])]
    info = {}
    for i in range(0, len(cand), 80):
        r = subprocess.run(['javap', '-p', '-cp', path] + cand[i:i+80], capture_output=True, text=True)
        cur = None
        for line in r.stdout.splitlines():
            m = re.match(r'.*class (\S+) extends (\S+)', line)
            if m:
                cur = m.group(1).split('.')[-1]; info[cur] = [m.group(2).split('.')[-1], set()]; continue
            if cur:
                for h in HOOKS:
                    if re.search(rf'\s{h}\(', line): info[cur][1].add(h)
    index = {snake(c).replace('_', ''): c for c in info}
    for it in items:
        key = it['id'].split(':')[1].replace('_', '')
        cls = index.get(key)
        if not cls:
            for k, c in index.items():
                if len(k) > 5 and (key.startswith(k) or k.startswith(key)): cls = c; break
        hooks = sorted(info[cls][1] - {'appendHoverText'}) if cls else []
        it['class'] = cls
        it['hooks'] = hooks
        it['verdict'] = 'special' if hooks else ('stats?' if cls else 'stats')
        results.append(it)
with open(out_path, 'w') as f:
    for r in results: f.write(json.dumps(r, ensure_ascii=False) + '\n')
c = collections.Counter(r['verdict'] for r in results)
print(len(results), c)
