#!/usr/bin/env python3
"""Builds the pack's .mrpack from instance/ and (with --publish) uploads it as a new version of the Modrinth project.

  ./release-modrinth.py 0.1.0                      build dist/Expedition-0.1.0.mrpack
  ./release-modrinth.py 0.1.0 --publish "notes"    build and upload (token in ~/.config/modrinth-token)

Mods come by link from Modrinth. A jar here whose hash Modrinth doesn't know is one flatten_jarjar.py rewrote: its
original (same name, or without "-shell", in mods-removed/) is linked instead, and the libraries it took out of the
originals are dropped (the originals carry them again). Jars Modrinth doesn't host at all go inside the pack
(OVERRIDE_JARS: our own, or licences that allow it). Mods only the server needs never are in instance/mods.
"""
import hashlib, io, json, os, re, sys, urllib.request, zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
INSTANCE = os.path.join(ROOT, 'instance')
MODS, REMOVED = os.path.join(INSTANCE, 'mods'), os.path.join(INSTANCE, 'mods-removed')
SERVER_MODS = os.path.join(ROOT, 'testserver', 'mods')
PROJECT_FILE = os.path.join(ROOT, '.modrinth-project')
NAME, MC, NEOFORGE = 'Expedition', '1.21.1', '21.1.266'
# Not on Modrinth; inside the pack: ours (core, atlas port, Nikita's catalysts), Twilight Forest (LGPL), AllTheLeaks (MIT).
OVERRIDE_JARS = ('expedition-', 'antique_atlas-', 'catalystwear-', 'twilightforest-', 'alltheleaks-')
# Config the pack ships (not options.txt: an update would reset every player's keys and settings).
OVERRIDE_DIRS = ('config', 'defaultconfigs', 'kubejs')
# Only the server needs these (login through Ely.by; pregeneration): left out of the players' pack.
SERVER_ONLY = ('neoforge_alternative_auth-', 'Chunky-')
SKIP = re.compile(r'^kubejs/(disabled|probe)/|\.bak|/logs?/')
API, UA = 'https://api.modrinth.com/v2', 'expedition-pack-release/1.0'


def api(path, data=None, token=None, method=None, headers=None):
    h = {'User-Agent': UA, **(headers or {})}
    if token:
        h['Authorization'] = token
    if isinstance(data, (dict, list)):
        data, h['Content-Type'] = json.dumps(data).encode(), 'application/json'
    req = urllib.request.Request(API + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            body = r.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        sys.exit(f'Modrinth {e.code} on {path}: {e.read().decode()[:500]}')


def sha1(path):
    return hashlib.sha1(open(path, 'rb').read()).hexdigest()


def nested(path_or_file):
    """Jar-in-jar names, nested ones too (C2ME's parts sit inside its "-all" library)."""
    out = set()
    with zipfile.ZipFile(path_or_file) as z:
        for n in z.namelist():
            if n.startswith('META-INF/') and n.endswith('.jar'):  # jarjar/ or jars/ (C2ME, tr7zw)
                out.add(os.path.basename(n))
                out |= nested(io.BytesIO(z.read(n)))
    return out


def resolve():
    """(files for the index, jars to put inside, problems)."""
    jars = sorted(f for f in os.listdir(MODS) if f.endswith('.jar') and not f.startswith(SERVER_ONLY))
    server = set(os.listdir(SERVER_MODS)) if os.path.isdir(SERVER_MODS) else set(jars)
    source = {}  # jar here -> path whose file Modrinth hosts
    for f in jars:
        source[f] = os.path.join(MODS, f)
    known = api('/version_files', {'hashes': [sha1(p) for p in source.values()], 'algorithm': 'sha1'})
    for f in jars:
        if sha1(source[f]) not in known:
            for name in (f, f.replace('-shell.jar', '.jar')):
                if os.path.exists(os.path.join(REMOVED, name)):
                    source[f] = os.path.join(REMOVED, name)
                    break
    known.update(api('/version_files', {'hashes': [sha1(p) for p in source.values() if p.startswith(REMOVED)], 'algorithm': 'sha1'}) or {})
    carried = set().union(*(nested(p) for p in source.values() if sha1(p) in known))
    files, inside, problems = [], [], []
    for f in jars:
        h = sha1(source[f])
        if h in known:
            file = next(x for x in known[h]['files'] if x['hashes']['sha1'] == h)
            files.append({'path': 'mods/' + file['filename'], 'hashes': {'sha1': h, 'sha512': file['hashes']['sha512']},
                          'env': {'client': 'required', 'server': 'required' if f in server else 'unsupported'},
                          'downloads': [file['url']], 'fileSize': file['size']})
        elif f.startswith(OVERRIDE_JARS):
            inside.append(f)
        elif f not in carried:
            problems.append(f)
    return files, inside, problems


def build(version):
    files, inside, problems = resolve()
    if problems:
        sys.exit('Not on Modrinth and not carried by another mod (add to OVERRIDE_JARS if its licence allows):\n  ' + '\n  '.join(problems))
    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': version, 'name': NAME, 'files': files,
             'dependencies': {'minecraft': MC, 'neoforge': NEOFORGE}}
    os.makedirs(os.path.join(ROOT, 'dist'), exist_ok=True)
    out = os.path.join(ROOT, 'dist', f'{NAME}-{version}.mrpack')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('modrinth.index.json', json.dumps(index, indent=1))
        for f in inside:
            z.write(os.path.join(MODS, f), 'overrides/mods/' + f)
        for d in OVERRIDE_DIRS:
            for base, _, names in os.walk(os.path.join(INSTANCE, d)):
                for n in names:
                    rel = os.path.relpath(os.path.join(base, n), INSTANCE)
                    if not SKIP.search(rel):
                        z.write(os.path.join(base, n), 'overrides/' + rel)
    print(f'{out}: {len(files)} mods by link, {len(inside)} inside ({", ".join(inside)}), {os.path.getsize(out) >> 20} MB')
    return out


def publish(version, path, notes):
    token = open(os.path.expanduser('~/.config/modrinth-token')).read().strip()
    if not os.path.exists(PROJECT_FILE):
        sys.exit(f'No project yet: create it on Modrinth (or let Claude do it) and put its id into {PROJECT_FILE}')
    project = open(PROJECT_FILE).read().strip()
    boundary = 'expedition' + hashlib.md5(os.urandom(8)).hexdigest()
    data = {'name': f'{NAME} {version}', 'version_number': version, 'changelog': notes, 'dependencies': [],
            'game_versions': [MC], 'version_type': 'beta', 'loaders': ['neoforge'], 'featured': True,
            'project_id': project, 'file_parts': ['pack'], 'primary_file': 'pack'}
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n\r\n{json.dumps(data)}\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="pack"; filename="{os.path.basename(path)}"\r\n'
            f'Content-Type: application/x-modrinth-modpack+zip\r\n\r\n').encode() + open(path, 'rb').read() + f'\r\n--{boundary}--\r\n'.encode()
    v = api('/version', body, token, 'POST', {'Content-Type': f'multipart/form-data; boundary={boundary}'})
    print(f'published {v["version_number"]}: https://modrinth.com/modpack/{project}/version/{v["id"]}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = build(sys.argv[1])
    if '--publish' in sys.argv:
        i = sys.argv.index('--publish')
        publish(sys.argv[1], out, sys.argv[i + 1] if len(sys.argv) > i + 1 else '')
