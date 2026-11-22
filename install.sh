#!/bin/sh
# Installs or updates the Expedition pack into a Minecraft folder (Linux, macOS):
#   curl -sL https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.sh | sh
#   curl -sL .../install.sh | sh -s -- /path/to/game/folder     (default: ~/.minecraft)
# Mods come from Modrinth (checked by hash), the pack's own files from the latest GitHub release. Mods the pack
# installed earlier and no longer has are removed; a mods folder from before the first install is kept aside.
# NeoForge is installed too when Java is found; then pick the "NeoForge 1.21.1" profile in your launcher.
set -e
DIR=${1:-$HOME/.minecraft}
PACK=${EXPEDITION_MRPACK:-https://github.com/somethink000/Create-rpg-mods-pack/releases/latest/download/Expedition.mrpack}
command -v python3 >/dev/null || { echo "Нужен python3"; exit 1; }
mkdir -p "$DIR"
exec python3 - "$DIR" "$PACK" <<'EOF'
import hashlib, io, json, os, shutil, subprocess, sys, time, urllib.request, zipfile
target, pack = sys.argv[1], sys.argv[2]
UA = {'User-Agent': 'expedition-installer/1.0'}

def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return r.read()
        except Exception:
            if attempt == 2:
                raise
            time.sleep(2)

print('Скачиваю сборку…')
data = open(pack, 'rb').read() if os.path.exists(pack) else fetch(pack)
mr = zipfile.ZipFile(io.BytesIO(data))
index = json.loads(mr.read('modrinth.index.json'))
mods = os.path.join(target, 'mods')
marker = os.path.join(target, '.expedition-files')
if os.path.isdir(mods) and not os.path.exists(marker) and os.listdir(mods):
    aside = mods + '-before-expedition'
    os.rename(mods, aside)
    print(f'Старые моды сохранены в {aside}')
previous = set(open(marker).read().split('\n')) if os.path.exists(marker) else set()
installed = []
files = [f for f in index['files'] if f.get('env', {}).get('client') != 'unsupported']
for n, f in enumerate(files, 1):
    path = os.path.join(target, f['path'])
    installed.append(f['path'])
    if os.path.exists(path) and hashlib.sha1(open(path, 'rb').read()).hexdigest() == f['hashes']['sha1']:
        continue
    print(f'[{n}/{len(files)}] {os.path.basename(path)}')
    body = fetch(f['downloads'][0])
    if hashlib.sha1(body).hexdigest() != f['hashes']['sha1']:
        sys.exit(f'Файл повреждён при скачивании: {f["path"]}')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(body)
for name in mr.namelist():
    for prefix in ('overrides/', 'client-overrides/'):
        if name.startswith(prefix) and not name.endswith('/'):
            rel = name[len(prefix):]
            path = os.path.join(target, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, 'wb').write(mr.read(name))
            installed.append(rel)
for rel in previous - set(installed):
    path = os.path.join(target, rel)
    if rel.startswith('mods/') and os.path.isfile(path):
        os.remove(path)
open(marker, 'w').write('\n'.join(sorted(set(installed))))
version = index['dependencies']['neoforge']
print(f'Сборка {index["versionId"]} установлена в {target}')
java = shutil.which('java')
if java:
    print(f'Ставлю NeoForge {version}…')
    installer = os.path.join(target, f'neoforge-{version}-installer.jar')
    open(installer, 'wb').write(fetch(f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar'))
    os.makedirs(os.path.join(target, 'versions'), exist_ok=True)
    profiles = os.path.join(target, 'launcher_profiles.json')
    if not os.path.exists(profiles):
        open(profiles, 'w').write('{"profiles":{}}')  # the NeoForge installer wants a launcher folder
    ok = subprocess.run([java, '-jar', installer, '--install-client', target], capture_output=True).returncode == 0
    os.remove(installer)
    print('NeoForge установлен.' if ok else f'NeoForge не встал: поставьте NeoForge {version} в лаунчере.')
else:
    print(f'Java не найдена: в лаунчере выберите или установите NeoForge {version} (Minecraft 1.21.1).')
print('Готово. Запустите NeoForge 1.21.1 и зайдите на сервер 212.22.85.153')
EOF
