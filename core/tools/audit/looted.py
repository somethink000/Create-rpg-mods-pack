# Finds opened (looted) containers in a world and the structure (if any) whose piece boxes contain them.
import sys, os, struct, zlib, gzip, io, glob, collections
sys.path.insert(0, os.path.dirname(__file__))
world = sys.argv[1]
def read_nbt(buf):
    def rd(fmt, n): return struct.unpack(fmt, buf.read(n))[0]
    def string(): return buf.read(rd('>H', 2)).decode('utf-8', 'replace')
    def payload(t):
        if t == 1: return rd('>b', 1)
        if t == 2: return rd('>h', 2)
        if t == 3: return rd('>i', 4)
        if t == 4: return rd('>q', 8)
        if t == 5: return rd('>f', 4)
        if t == 6: return rd('>d', 8)
        if t == 7: return buf.read(rd('>i', 4))
        if t == 8: return string()
        if t == 9:
            et, n = rd('>b', 1), rd('>i', 4); return [payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = rd('>b', 1)
                if tt == 0: return out
                name = string(); out[name] = payload(tt)
        if t == 11: n = rd('>i', 4); return list(struct.unpack(f'>{n}i', buf.read(4 * n)))
        if t == 12: n = rd('>i', 4); buf.read(8 * n); return None
    t = rd('>b', 1); string(); return payload(t)
chests, pieces = [], []
for path in glob.glob(os.path.join(world, 'region', '*.mca')):
    with open(path, 'rb') as f:
        header = f.read(4096)
        for i in range(1024):
            loc = struct.unpack('>I', header[4 * i:4 * i + 4])[0]
            if not loc: continue
            f.seek((loc >> 8) * 4096); length = struct.unpack('>I', f.read(4))[0]; comp = f.read(1)[0]
            data = f.read(length - 1)
            try: raw = zlib.decompress(data) if comp == 2 else gzip.decompress(data) if comp == 1 else data
            except Exception: continue
            nbt = read_nbt(io.BytesIO(raw))
            for be in nbt.get('block_entities', []):
                if 'Items' in be and 'LootTable' not in be and be.get('id', '').endswith(('chest', 'barrel', 'shulker_box')) is not False:
                    if len(be.get('Items', [])) >= 0 and be.get('id') in ('minecraft:chest', 'minecraft:barrel', 'minecraft:trapped_chest'):
                        chests.append((be['x'], be['y'], be['z']))
            for sid, st in ((nbt.get('structures') or {}).get('starts') or {}).items():
                if st.get('id') == 'INVALID': continue
                for ch in st.get('Children', []):
                    bb = ch.get('BB')
                    if bb: pieces.append((sid, bb))
found = collections.Counter()
for x, y, z in chests:
    owner = next((sid for sid, bb in pieces if bb[0] <= x <= bb[3] and bb[1] <= y <= bb[4] and bb[2] <= z <= bb[5]), None)
    found[owner or '(not in a structure piece)'] += 1
for k, v in found.most_common(): print(v, k)
print(len(chests), 'opened containers')
