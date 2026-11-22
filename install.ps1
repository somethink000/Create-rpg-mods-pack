# Installs or updates the Expedition pack (Windows, PowerShell):
#   irm https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.ps1 | iex
# The pack gets its own folder, %APPDATA%\.minecraft\home\Expedition ($env:EXPEDITION_DIR overrides it), so it never mixes
# with other mods. A version "Expedition" (NeoForge underneath) and a launcher profile pointing at that folder make it show
# up in the official launcher and in Legacy Launcher (its "separate folder for each version" mode uses home\<version>).
# Mods come from Modrinth (checked by hash), the pack's own files from Expedition.mrpack in the repository; mods the pack
# installed earlier and no longer has are removed. NeoForge is installed with Java from PATH or from a launcher.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$packName = 'Expedition'
$mcRoot = Join-Path $env:APPDATA '.minecraft'
$target = if ($env:EXPEDITION_DIR) { $env:EXPEDITION_DIR } else { Join-Path $mcRoot "home\$packName" }
$pack = if ($env:EXPEDITION_MRPACK) { $env:EXPEDITION_MRPACK } else { 'https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/Expedition.mrpack' }
# A running launcher rewrites launcher_profiles.json on exit and would drop the profile added below
# (Minecraft.exe is the Microsoft Store launcher, MinecraftLauncher.exe the standalone one).
if (Get-Process -Name 'Minecraft', 'MinecraftLauncher' -ErrorAction SilentlyContinue) { throw 'Закройте лаунчер Minecraft и запустите установку снова.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
# Launchers read their JSON as UTF-8 without BOM, which Set-Content can't write on Windows PowerShell.
$utf8 = New-Object Text.UTF8Encoding $false
# File names are taken as they are (.NET calls, -LiteralPath): mods like creategoggles-...-[NEOFORGE].jar have brackets,
# which PowerShell's -Path and -OutFile read as wildcards.
$web = New-Object Net.WebClient
$web.Headers.Add('User-Agent', 'expedition-installer/1.0')
function Get-Sha1($path) { (Get-FileHash -Algorithm SHA1 -LiteralPath $path).Hash.ToLower() }
function New-Dir($path) { [void][IO.Directory]::CreateDirectory($path) }
function Get-File($url, $path) { New-Dir ([IO.Path]::GetDirectoryName($path)); $web.DownloadFile($url, $path) }

Write-Host 'Скачиваю сборку…'
New-Dir $target
$tmp = Join-Path $env:TEMP 'Expedition.mrpack'
if (Test-Path -LiteralPath $pack) { Copy-Item -LiteralPath $pack $tmp -Force } else { Get-File $pack $tmp }
$zip = [IO.Compression.ZipFile]::OpenRead($tmp)
try {
    $reader = New-Object IO.StreamReader($zip.GetEntry('modrinth.index.json').Open())
    $index = $reader.ReadToEnd() | ConvertFrom-Json
    $reader.Close()
    $mods = Join-Path $target 'mods'
    $marker = Join-Path $target '.expedition-files'
    if ((Test-Path -LiteralPath $mods) -and -not (Test-Path -LiteralPath $marker) -and (Get-ChildItem -LiteralPath $mods | Select-Object -First 1)) {
        Rename-Item -LiteralPath $mods -NewName 'mods-before-expedition'
        Write-Host "Старые моды сохранены в $mods-before-expedition"
    }
    $previous = if (Test-Path -LiteralPath $marker) { Get-Content -LiteralPath $marker } else { @() }
    $installed = New-Object System.Collections.Generic.List[string]
    $files = @($index.files | Where-Object { $_.env.client -ne 'unsupported' })
    $n = 0
    foreach ($f in $files) {
        $n++
        $path = Join-Path $target $f.path
        $installed.Add($f.path)
        if ((Test-Path -LiteralPath $path) -and (Get-Sha1 $path) -eq $f.hashes.sha1) { continue }
        Write-Host "[$n/$($files.Count)] $(Split-Path $f.path -Leaf)"
        Get-File $f.downloads[0] $path
        if ((Get-Sha1 $path) -ne $f.hashes.sha1) { throw "Файл повреждён при скачивании: $($f.path)" }
    }
    foreach ($entry in $zip.Entries) {
        foreach ($prefix in 'overrides/', 'client-overrides/') {
            if ($entry.FullName.StartsWith($prefix) -and -not $entry.FullName.EndsWith('/')) {
                $rel = $entry.FullName.Substring($prefix.Length)
                $path = Join-Path $target $rel
                New-Dir ([IO.Path]::GetDirectoryName($path))
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $path, $true)
                $installed.Add($rel)
            }
        }
    }
} finally { $zip.Dispose() }
foreach ($rel in $previous) {
    $path = Join-Path $target $rel
    if ($rel.StartsWith('mods/') -and -not $installed.Contains($rel) -and (Test-Path -LiteralPath $path)) { Remove-Item -LiteralPath $path }
}
[IO.File]::WriteAllLines($marker, [string[]]($installed | Sort-Object -Unique), $utf8)
Write-Host "Сборка $($index.versionId) установлена в $target"

# NeoForge goes into .minecraft (versions, libraries), where launchers look for it.
$version = $index.dependencies.neoforge
$neoforgeId = "neoforge-$version"
$profiles = Join-Path $mcRoot 'launcher_profiles.json'
$neoforgeJson = Join-Path $mcRoot "versions\$neoforgeId\$neoforgeId.json"
if (-not (Test-Path -LiteralPath $neoforgeJson)) {
    # Java from PATH, else the one the official launcher or Legacy Launcher downloaded for itself.
    $java = (Get-Command java -ErrorAction SilentlyContinue).Source
    if (-not $java) {
        $java = @("$mcRoot\runtime", "$env:LOCALAPPDATA\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime", "$env:APPDATA\.tlauncher") |
            Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Get-ChildItem -LiteralPath $_ -Recurse -Filter 'java.exe' -ErrorAction SilentlyContinue } |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if ($java) {
        Write-Host "Ставлю NeoForge $version…"
        $installer = Join-Path $env:TEMP "$neoforgeId-installer.jar"
        Get-File "https://maven.neoforged.net/releases/net/neoforged/neoforge/$version/$neoforgeId-installer.jar" $installer
        if (-not (Test-Path -LiteralPath $profiles)) { [IO.File]::WriteAllText($profiles, '{"profiles":{}}', $utf8) }
        & $java -jar $installer --install-client $mcRoot | Out-Null
        Remove-Item -LiteralPath $installer
    }
}
if (-not (Test-Path -LiteralPath $neoforgeJson)) {
    Write-Host "Не удалось поставить NeoForge: установите в лаунчере NeoForge $version (Minecraft 1.21.1) и запустите эту команду ещё раз."
    return
}

# The pack's own version on top of NeoForge: Legacy Launcher has no profiles and picks the game folder by version name.
# No "jar" field on purpose: NeoForge drops ${version_name}.jar from the classpath, so the jar stays Expedition.jar.
$now = (Get-Date).ToUniversalTime().ToString('o')
$versionDir = Join-Path $mcRoot "versions\$packName"
New-Dir $versionDir
$versionJson = [pscustomobject]@{ id = $packName; inheritsFrom = $neoforgeId; type = 'release'; time = $now; releaseTime = $now }
[IO.File]::WriteAllText((Join-Path $versionDir "$packName.json"), ($versionJson | ConvertTo-Json), $utf8)
# The official launcher picks the game folder from a profile. Only what the pack owns is updated, so the player's own
# settings (memory, resolution) survive an update; a new profile starts with the memory the pack needs.
$launcher = if (Test-Path -LiteralPath $profiles) { [IO.File]::ReadAllText($profiles) | ConvertFrom-Json } else { [pscustomobject]@{ profiles = [pscustomobject]@{} } }
if (-not $launcher.profiles) { $launcher | Add-Member -Force -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) }
$existing = $launcher.profiles.$packName
if ($existing) {
    $existing | Add-Member -Force -NotePropertyName lastVersionId -NotePropertyValue $packName
    $existing | Add-Member -Force -NotePropertyName gameDir -NotePropertyValue $target
} else {
    $launcher.profiles | Add-Member -NotePropertyName $packName -NotePropertyValue ([pscustomobject]@{
        name = $packName; type = 'custom'; icon = 'Furnace'; created = $now
        lastVersionId = $packName; gameDir = $target; javaArgs = '-Xmx6G -Xms2G'
    })
}
[IO.File]::WriteAllText($profiles, ($launcher | ConvertTo-Json -Depth 10), $utf8)
Write-Host 'Готово.'
Write-Host "  Официальный лаунчер: выберите установку «$packName»."
Write-Host "  Legacy Launcher / TLauncher: включите «Отдельная папка для каждой версии», выберите версию «$packName» и выделите 6 ГБ памяти."
Write-Host 'Сервер: 212.22.85.153'
