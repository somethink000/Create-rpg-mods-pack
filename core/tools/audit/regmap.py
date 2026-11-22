# Map item id -> constructed class by reading registration bytecode: `ldc "<id>"` followed by an invokedynamic whose
# bootstrap target is either `Foo::new` or a lambda whose body does `new Foo`. Prints JSON {jar: {id: class}}.
import zipfile, subprocess, re, sys, os, json
mods, wanted_jars = sys.argv[1], sys.argv[2].split('|')
result = {}
for jar in os.listdir(mods):
    if not any(w in jar for w in wanted_jars): continue
    path = os.path.join(mods, jar)
    z = zipfile.ZipFile(path)
    # only classes that contain registration-like string constants
    regs = []
    for n in z.namelist():
        if n.endswith('.class') and re.search(r'(Item|Registry|Registr|Init)', n.split('/')[-1]):
            regs.append(n[:-6].replace('/', '.'))
    mapping = {}
    for i in range(0, len(regs), 20):
        out = subprocess.run(['javap', '-c', '-p', '-v', '-cp', path] + regs[i:i+20], capture_output=True, text=True).stdout
        for block in out.split('\nClassfile ')[0:]:
            # bootstrap index -> target
            boot = {}
            bm = re.search(r'BootstrapMethods:\n(.*?)(?:\n\S|\Z)', block, re.S)
            if bm:
                entries = re.split(r'\n\s+(\d+): #', bm.group(1))
                for k in range(1, len(entries) - 1, 2):
                    idx, body = int(entries[k]), entries[k + 1]
                    t = re.search(r'REF_newInvokeSpecial ([\w/$]+)\.\"<init>\"', body)
                    l = re.search(r'REF_invokeStatic [\w/$]+\.(lambda\$[\w$]+):', body)
                    boot[idx] = ('new', t.group(1).split('/')[-1]) if t else (('lambda', l.group(1)) if l else None)
            # constant pool: InvokeDynamic #bootIdx
            indy = {m.group(1): int(m.group(2)) for m in re.finditer(r'#(\d+) = InvokeDynamic\s+#(\d+):', block)}
            # lambda bodies -> first `new`
            lambdas = {}
            for m in re.finditer(r'static [^\n]*?(lambda\$[\w$]+)\([^\n]*\n(.*?)(?=\n  [a-z][^\n]*\(|\n}\Z|\Z)', block, re.S):
                nw = re.search(r'// class ([\w/$]+)\n', m.group(2))
                if nw: lambdas[m.group(1)] = nw.group(1).split('/')[-1]
            last = None
            for line in block.splitlines():
                s = re.search(r'ldc(?:_w)?\s+#\d+\s+// String ([a-z0-9_/]+)$', line)
                if s: last = s.group(1); continue
                d = re.search(r'invokedynamic #(\d+),', line)
                if d and last:
                    b = boot.get(indy.get(d.group(1), -1))
                    if b:
                        cls = b[1] if b[0] == 'new' else lambdas.get(b[1])
                        if cls and last not in mapping: mapping[last] = cls
                    last = None
                elif re.search(r'\bnew\s+#\d+\s+// class ([\w/$]+)', line) and last:
                    cls = re.search(r'// class ([\w/$]+)', line).group(1).split('/')[-1]
                    if last not in mapping and not cls.startswith(('Item$Properties', 'ResourceLocation')): mapping[last] = cls
    result[jar] = mapping
json.dump(result, sys.stdout)
