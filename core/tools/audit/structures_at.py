# Lists structure starts recorded in a world's region files within a radius (in chunks) of a block position.
import sys, os, struct, zlib, gzip, io, collections
world, bx, bz, radius = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
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
        if t == 11: n = rd('>i', 4); buf.read(4 * n); return None
        if t == 12: n = rd('>i', 4); buf.read(8 * n); return None
    t = rd('>b', 1); string(); return payload(t)
cx0, cz0 = bx >> 4, bz >> 4
found = collections.Counter()
for cx in range(cx0 - radius, cx0 + radius + 1):
    for cz in range(cz0 - radius, cz0 + radius + 1):
        path = os.path.join(world, 'region', f'r.{cx >> 5}.{cz >> 5}.mca')
        if not os.path.exists(path): continue
        with open(path, 'rb') as f:
            idx = 4 * ((cx & 31) + (cz & 31) * 32); f.seek(idx)
            loc = struct.unpack('>I', f.read(4))[0]
            if loc == 0: continue
            f.seek((loc >> 8) * 4096); length = struct.unpack('>I', f.read(4))[0]; comp = f.read(1)[0]
            data = f.read(length - 1)
            raw = zlib.decompress(data) if comp == 2 else gzip.decompress(data) if comp == 1 else data
            nbt = read_nbt(io.BytesIO(raw))
            starts = (nbt.get('structures') or {}).get('starts') or {}
            for sid, st in starts.items():
                if st.get('id') != 'INVALID':
                    bb = st.get('Children', [{}])[0].get('BB') if st.get('Children') else None
                    found[f"{sid} start chunk {cx},{cz} pieces={len(st.get('Children', []))}"] += 1
for k in sorted(found): print(k)
print('scanned radius', radius, 'chunks around', cx0, cz0)
