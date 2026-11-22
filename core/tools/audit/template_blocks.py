# Counts valuable blocks placed by structure templates (NBT) across all mod jars.
import zipfile, gzip, struct, io, os, re, sys, collections
mods = sys.argv[1]
VALUABLE = re.compile(r'minecraft:(\w+_wool|\w+_carpet|gold_block|iron_block|diamond_block|emerald_block|lapis_block|redstone_block|copper_block|cut_copper|raw_\w+_block|bookshelf|amethyst_block|honey_block|beacon|conduit|netherite_block|obsidian|crying_obsidian)$')
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
total, by_mod = collections.Counter(), collections.defaultdict(collections.Counter)
for name in os.listdir(mods):
    if not name.endswith('.jar'): continue
    try: z = zipfile.ZipFile(os.path.join(mods, name))
    except Exception: continue
    for n in z.namelist():
        m = re.match(r'data/([^/]+)/structures?/.+\.nbt$', n)
        if not m: continue
        try:
            raw = z.read(n)
            try: raw = gzip.decompress(raw)
            except Exception: pass
            nbt = read_nbt(io.BytesIO(raw))
        except Exception: continue
        if not isinstance(nbt, dict): continue
        palette = nbt.get('palette') or (nbt.get('palettes') or [[]])[0]
        names = [p.get('Name', '') for p in palette]
        for b in nbt.get('blocks', []):
            st = b.get('state', -1)
            if 0 <= st < len(names) and VALUABLE.search(names[st]):
                key = re.sub(r'^(minecraft:)(\w+?)_(wool|carpet)$', r'\1*_\3', names[st])
                total[key] += 1; by_mod[m.group(1)][key] += 1
for k, v in total.most_common(20): print(f'{v:>8}  {k}')
print('--- wool by mod:')
for ns, c in sorted(by_mod.items(), key=lambda kv: -kv[1]['minecraft:*_wool'])[:8]: print(f"{c['minecraft:*_wool']:>8}  {ns}")
