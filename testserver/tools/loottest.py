# Fill a chest from a loot table N times via RCON and count how often given item ids appear.
import sys, re, subprocess
table, n, pattern = sys.argv[1], int(sys.argv[2]), sys.argv[3]
X = int(sys.argv[4]) if len(sys.argv) > 4 else 0
def rcon(*cmds):
    return subprocess.run(['python3', '-I', 'tools/rcon.py', *cmds], capture_output=True, text=True).stdout
rcon(f'forceload add {X} 0', f'setblock {X} 200 0 minecraft:chest')
hits, items, units = 0, 0, 0
for i in range(n):
    out = rcon(f'setblock {X} 200 0 minecraft:air', f'setblock {X} 200 0 minecraft:chest', f'execute positioned {X} 200 0 run loot insert {X} 200 0 loot {table}', f'data get block {X} 200 0 Items')
    found = re.findall(r'id: "([a-z0-9_]+:[a-z0-9_]+)"', out)
    units += sum(int(c) for c, i in re.findall(r'count: (\d+), Slot: \d+b, id: "([a-z0-9_]+:[a-z0-9_]+)"', out) if re.search(pattern, i))
    items += len(found)
    m = [f for f in found if re.search(pattern, f)]; hits += len(m); 
print(f'{table}: {n} fills, {items} stacks, {hits} matching {pattern}, {units} items = {units/n:.1f} per fill')
