#!/bin/sh
# Installs or updates the Expedition pack (Linux, macOS):
#   curl -sL https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.sh | sh
# The pack gets its own folder, <minecraft>/home/Expedition (EXPEDITION_DIR overrides it), so it never mixes with other
# mods. A version "Expedition" (NeoForge underneath) and a launcher profile pointing at that folder make it show up in the
# official launcher and in Legacy Launcher (its "separate folder for each version" mode uses home/<version>).
# Mods come from Modrinth (checked by hash), the pack's own files from Expedition.mrpack in the repository; mods the pack
# installed earlier and no longer has are removed. NeoForge is installed with Java from PATH or from a launcher.
set -e
command -v python3 >/dev/null || { echo "Нужен python3"; exit 1; }
exec python3 - <<'EOF'
import datetime, glob, hashlib, io, json, os, shutil, subprocess, sys, time, urllib.request, zipfile

PACK_NAME = 'Expedition'
home = os.path.expanduser('~')
mc_root = os.path.join(home, 'Library', 'Application Support', 'minecraft') if sys.platform == 'darwin' else os.path.join(home, '.minecraft')
target = os.environ.get('EXPEDITION_DIR') or os.path.join(mc_root, 'home', PACK_NAME)
pack = os.environ.get('EXPEDITION_MRPACK') or 'https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/Expedition.mrpack'
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


def sha1(path):
    return hashlib.sha1(open(path, 'rb').read()).hexdigest()


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(data)


print('Скачиваю сборку…', flush=True)
os.makedirs(target, exist_ok=True)
mr = zipfile.ZipFile(io.BytesIO(open(pack, 'rb').read() if os.path.exists(pack) else fetch(pack)))
index = json.loads(mr.read('modrinth.index.json'))
mods = os.path.join(target, 'mods')
marker = os.path.join(target, '.expedition-files')
if os.path.isdir(mods) and not os.path.exists(marker) and os.listdir(mods):
    os.rename(mods, mods + '-before-expedition')
    print(f'Старые моды сохранены в {mods}-before-expedition')
previous = set(open(marker).read().split('\n')) if os.path.exists(marker) else set()
installed = []
files = [f for f in index['files'] if f.get('env', {}).get('client') != 'unsupported']
for n, f in enumerate(files, 1):
    path = os.path.join(target, f['path'])
    installed.append(f['path'])
    if os.path.exists(path) and sha1(path) == f['hashes']['sha1']:
        continue
    print(f'[{n}/{len(files)}] {os.path.basename(path)}', flush=True)
    body = fetch(f['downloads'][0])
    if hashlib.sha1(body).hexdigest() != f['hashes']['sha1']:
        sys.exit(f'Файл повреждён при скачивании: {f["path"]}')
    write(path, body)
for name in mr.namelist():
    for prefix in ('overrides/', 'client-overrides/'):
        if name.startswith(prefix) and not name.endswith('/'):
            rel = name[len(prefix):]
            write(os.path.join(target, rel), mr.read(name))
            installed.append(rel)
for rel in previous - set(installed):
    path = os.path.join(target, rel)
    if rel.startswith('mods/') and os.path.isfile(path):
        os.remove(path)
open(marker, 'w').write('\n'.join(sorted(set(installed))))
print(f'Сборка {index["versionId"]} установлена в {target}')

# NeoForge goes into the Minecraft folder (versions, libraries), where launchers look for it.
version = index['dependencies']['neoforge']
neoforge_id = f'neoforge-{version}'
profiles = os.path.join(mc_root, 'launcher_profiles.json')
neoforge_json = os.path.join(mc_root, 'versions', neoforge_id, neoforge_id + '.json')
if not os.path.exists(neoforge_json):
    # Java from PATH, else the one the official launcher or Legacy Launcher downloaded for itself.
    java = shutil.which('java') or next(iter(sorted(
        glob.glob(os.path.join(mc_root, 'runtime', '**', 'bin', 'java'), recursive=True)
        + glob.glob(os.path.join(home, '.tlauncher', '**', 'bin', 'java'), recursive=True))), None)
    if java:
        print(f'Ставлю NeoForge {version}…', flush=True)
        installer = os.path.join(target, neoforge_id + '-installer.jar')
        write(installer, fetch(f'https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/{neoforge_id}-installer.jar'))
        if not os.path.exists(profiles):
            write(profiles, b'{"profiles":{}}')  # the NeoForge installer wants a launcher folder
        subprocess.run([java, '-jar', installer, '--install-client', mc_root], capture_output=True, cwd=target)
        for leftover in (installer, installer + '.log'):
            if os.path.exists(leftover):
                os.remove(leftover)
if not os.path.exists(neoforge_json):
    sys.exit(f'Не удалось поставить NeoForge: установите в лаунчере NeoForge {version} (Minecraft 1.21.1) и запустите эту команду ещё раз.')

# The pack's own version on top of NeoForge: Legacy Launcher has no profiles and picks the game folder by version name.
# No "jar" field on purpose: NeoForge drops ${version_name}.jar from the classpath, so the jar stays Expedition.jar.
now = datetime.datetime.now(datetime.timezone.utc).isoformat()
write(os.path.join(mc_root, 'versions', PACK_NAME, PACK_NAME + '.json'),
      json.dumps({'id': PACK_NAME, 'inheritsFrom': neoforge_id, 'type': 'release', 'time': now, 'releaseTime': now}, indent=2).encode())
# The official launcher picks the game folder from a profile. Only what the pack owns is updated, so the player's own
# settings (memory, resolution) survive an update; a new profile starts with the memory the pack needs.
launcher = json.load(open(profiles, encoding='utf-8')) if os.path.exists(profiles) else {}
profile = launcher.setdefault('profiles', {}).setdefault(PACK_NAME, {
    'name': PACK_NAME, 'type': 'custom', 'icon': 'Furnace', 'created': now, 'javaArgs': '-Xmx6G -Xms2G'})
profile.update(lastVersionId=PACK_NAME, gameDir=target)
write(profiles, json.dumps(launcher, indent=2, ensure_ascii=False).encode())
print('Готово.')
print(f'  Официальный лаунчер: выберите установку «{PACK_NAME}».')
print(f'  Legacy Launcher / TLauncher: включите «Отдельная папка для каждой версии», выберите версию «{PACK_NAME}» и выделите 6 ГБ памяти.')
print('Сервер: 212.22.85.153')
EOF
