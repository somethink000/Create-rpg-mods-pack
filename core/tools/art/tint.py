# Makes item textures by tinting vanilla ones: tint.py <resources.jar> <out_dir> name=base:RRGGBB ...
import sys, zipfile, zlib, struct, os

def read_png(data):
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos, idat, w = 8, b'', 0
    while pos < len(data):
        n, typ = struct.unpack('>I4s', data[pos:pos + 8]); body = data[pos + 8:pos + 8 + n]; pos += 12 + n
        if typ == b'IHDR': w, h, depth, ctype = struct.unpack('>IIBB', body[:10]); assert depth == 8 or ctype in (0, 3), depth
        elif typ == b'PLTE': plte = body
        elif typ == b'tRNS': trns = body
        elif typ == b'IDAT': idat += body
    raw = zlib.decompress(idat)
    bpp = {6: 4, 2: 3, 3: 1, 4: 2, 0: 1}[ctype]
    stride = w * bpp if depth == 8 else (w * depth + 7) // 8
    rows, prev = [], bytearray(stride)
    trns = locals().get('trns', b'')
    for y in range(h):
        f, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0; b = prev[i]; c = prev[i - bpp] if i >= bpp else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line); prev = line
    px = []
    for line in rows:
        for x in range(w):
            if ctype == 6: px.append(tuple(line[x * 4:x * 4 + 4]))
            elif ctype == 2: px.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            elif ctype == 3:
                i = line[x] if depth == 8 else (line[x * depth // 8] >> (8 - depth - (x * depth) % 8)) & ((1 << depth) - 1)
                px.append(tuple(plte[i * 3:i * 3 + 3]) + ((trns[i] if i < len(trns) else 255),))
            elif ctype == 4: px.append((line[x * 2],) * 3 + (line[x * 2 + 1],))
            else: px.append((line[x],) * 3 + (255,))
    return w, h, px

def write_png(path, w, h, px):
    raw = b''.join(b'\x00' + bytes(sum((list(p) for p in px[y * w:(y + 1) * w]), [])) for y in range(h))
    chunk = lambda t, b: struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

jar, out = zipfile.ZipFile(sys.argv[1]), sys.argv[2]
os.makedirs(out, exist_ok=True)
for spec in sys.argv[3:]:
    name, rest = spec.split('='); base, color = rest.split(':')
    tr, tg, tb = int(color[0:2], 16), int(color[2:4], 16), int(color[4:6], 16)
    w, h, px = read_png(jar.read(f'assets/minecraft/textures/item/{base}.png'))
    res = []
    for r, g, b, a in px:
        l = (0.3 * r + 0.59 * g + 0.11 * b) / 255 * 1.25
        res.append((min(255, int(tr * l)), min(255, int(tg * l)), min(255, int(tb * l)), a))
    write_png(os.path.join(out, name + '.png'), w, h, res)
    print(name, w, h)
