# Combat module v2: exact item->class from registration bytecode (regmap.json), hooks incl. same-jar superclasses.
import zipfile, subprocess, json, re, sys, os, collections
mods, catalog_path, regmap_path, out_path = sys.argv[1:5]
HOOKS = ['use', 'useOn', 'hurtEnemy', 'postHurtEnemy', 'inventoryTick', 'releaseUsing', 'onUseTick', 'finishUsingItem',
         'onLeftClickEntity', 'onEntitySwing', 'onArmorTick', 'canElytraFly', 'interactLivingEntity', 'curioTick', 'onEquip',
         'onUnequip', 'mineBlock', 'onCraftedBy', 'elytraFlightTick', 'onDroppedByPlayer', 'onEntityItemUpdate', 'damageItem']
GEAR = {'weapon', 'armor', 'shield', 'tool'}
VANILLA = {'Item', 'SwordItem', 'AxeItem', 'PickaxeItem', 'ShovelItem', 'HoeItem', 'DiggerItem', 'TieredItem', 'ArmorItem',
           'ShieldItem', 'BowItem', 'CrossbowItem', 'TridentItem', 'BlockItem', 'DeferredSpawnEggItem', 'SpawnEggItem', 'AnimalArmorItem', 'MaceItem'}
cat = [json.loads(l) for l in open(catalog_path)]
regmap = json.load(open(regmap_path))
by_jar = collections.defaultdict(list)
for r in cat:
    if r['module'] in GEAR: by_jar[r['jar']].append(r)
results = []
for jar, items in by_jar.items():
    path = os.path.join(mods, jar)
    names = {n[:-6].split('/')[-1]: n[:-6].replace('/', '.') for n in zipfile.ZipFile(path).namelist() if n.endswith('.class')}
    mapping = regmap.get(jar, {})
    cache = {}
    def hooks_of(simple, depth=0):
        if simple in cache: return cache[simple]
        if simple in VANILLA or simple not in names or depth > 6: return set()
        out = subprocess.run(['javap', '-p', '-cp', path, names[simple]], capture_output=True, text=True).stdout
        m = re.search(r'class \S+ extends (\S+)', out)
        hs = {h for h in HOOKS if re.search(rf'\s{h}\(', out)}
        if m: hs |= hooks_of(m.group(1).split('.')[-1].split('<')[0], depth + 1)
        cache[simple] = hs
        return hs
    for it in items:
        cls = mapping.get(it['id'].split(':')[1])
        hs = sorted(hooks_of(cls)) if cls else []
        it['class'] = cls
        it['hooks'] = hs
        if hs: it['verdict'] = 'special'
        elif cls is None or cls in VANILLA: it['verdict'] = 'stats'
        else: it['verdict'] = 'stats?'
        results.append(it)
with open(out_path, 'w') as f:
    for r in results: f.write(json.dumps(r, ensure_ascii=False) + '\n')
print(len(results), collections.Counter(r['verdict'] for r in results), 'unmapped:', sum(1 for r in results if r['class'] is None))
