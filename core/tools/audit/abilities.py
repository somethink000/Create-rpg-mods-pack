# For one mod jar: item classes and which gameplay hooks they override (= the item does something special).
import zipfile, subprocess, sys, re, os, tempfile
jar = sys.argv[1]
HOOKS = ['use(', 'useOn(', 'hurtEnemy(', 'postHurtEnemy(', 'inventoryTick(', 'releaseUsing(', 'onUseTick(', 'finishUsingItem(',
         'onLeftClickEntity(', 'onEntitySwing(', 'onArmorTick(', 'canElytraFly(', 'getAttributeModifiers(', 'interactLivingEntity(',
         'curioTick(', 'onEquip(', 'onUnequip(', 'getDefaultAttributeModifiers(', 'mineBlock(', 'canPerformAction(']
z = zipfile.ZipFile(jar)
classes = [n[:-6].replace('/', '.') for n in z.namelist() if n.endswith('.class') and re.search(r'/(item|items|weapon|armor|equipment|tools?)/', n, re.I)]
out = []
for i in range(0, len(classes), 60):
    chunk = classes[i:i+60]
    r = subprocess.run(['javap', '-p', '-cp', jar] + chunk, capture_output=True, text=True)
    cur = None
    for line in r.stdout.splitlines():
        m = re.match(r'.*class (\S+) extends (\S+)', line)
        if m:
            cur = [m.group(1).split('.')[-1], m.group(2).split('.')[-1], set()]
            out.append(cur)
            continue
        if cur:
            for h in HOOKS:
                if ' ' + h in line: cur[2].add(h[:-1])
for name, parent, hooks in sorted(out):
    if '$' in name: continue
    print(f'{name:<40} extends {parent:<28} {",".join(sorted(hooks)) or "-"}')
