# Installs or updates the Expedition pack into a Minecraft folder (Windows, PowerShell):
#   irm https://raw.githubusercontent.com/somethink000/Create-rpg-mods-pack/main/install.ps1 | iex
# The folder is %APPDATA%\.minecraft unless $env:EXPEDITION_DIR says otherwise. Mods come from Modrinth (checked by
# hash), the pack's own files from the latest GitHub release. Mods the pack installed earlier and no longer has are
# removed; a mods folder from before the first install is kept aside. NeoForge is installed too when Java is found.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$target = if ($env:EXPEDITION_DIR) { $env:EXPEDITION_DIR } else { Join-Path $env:APPDATA '.minecraft' }
$pack = if ($env:EXPEDITION_MRPACK) { $env:EXPEDITION_MRPACK } else { 'https://github.com/somethink000/Create-rpg-mods-pack/releases/latest/download/Expedition.mrpack' }
New-Item -ItemType Directory -Force -Path $target | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-Sha1($path) { (Get-FileHash -Algorithm SHA1 -LiteralPath $path).Hash.ToLower() }

Write-Host 'Скачиваю сборку…'
$tmp = Join-Path $env:TEMP 'Expedition.mrpack'
if (Test-Path $pack) { Copy-Item $pack $tmp -Force } else { Invoke-WebRequest -UseBasicParsing $pack -OutFile $tmp }
$zip = [IO.Compression.ZipFile]::OpenRead($tmp)
try {
    $reader = New-Object IO.StreamReader($zip.GetEntry('modrinth.index.json').Open())
    $index = $reader.ReadToEnd() | ConvertFrom-Json
    $reader.Close()
    $mods = Join-Path $target 'mods'
    $marker = Join-Path $target '.expedition-files'
    if ((Test-Path $mods) -and -not (Test-Path $marker) -and (Get-ChildItem $mods | Select-Object -First 1)) {
        Rename-Item $mods ($mods + '-before-expedition')
        Write-Host "Старые моды сохранены в $mods-before-expedition"
    }
    $previous = if (Test-Path $marker) { Get-Content $marker } else { @() }
    $installed = New-Object System.Collections.Generic.List[string]
    $files = @($index.files | Where-Object { $_.env.client -ne 'unsupported' })
    $n = 0
    foreach ($f in $files) {
        $n++
        $path = Join-Path $target $f.path
        $installed.Add($f.path)
        if ((Test-Path $path) -and (Get-Sha1 $path) -eq $f.hashes.sha1) { continue }
        Write-Host "[$n/$($files.Count)] $(Split-Path $f.path -Leaf)"
        New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
        Invoke-WebRequest -UseBasicParsing $f.downloads[0] -OutFile $path
        if ((Get-Sha1 $path) -ne $f.hashes.sha1) { throw "Файл повреждён при скачивании: $($f.path)" }
    }
    foreach ($entry in $zip.Entries) {
        foreach ($prefix in 'overrides/', 'client-overrides/') {
            if ($entry.FullName.StartsWith($prefix) -and -not $entry.FullName.EndsWith('/')) {
                $rel = $entry.FullName.Substring($prefix.Length)
                $path = Join-Path $target $rel
                New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $path, $true)
                $installed.Add($rel)
            }
        }
    }
} finally { $zip.Dispose() }
foreach ($rel in $previous) {
    $path = Join-Path $target $rel
    if ($rel.StartsWith('mods/') -and -not $installed.Contains($rel) -and (Test-Path $path)) { Remove-Item $path }
}
$installed | Sort-Object -Unique | Set-Content $marker
$version = $index.dependencies.neoforge
Write-Host "Сборка $($index.versionId) установлена в $target"
$java = Get-Command java -ErrorAction SilentlyContinue
if ($java) {
    Write-Host "Ставлю NeoForge $version…"
    $installer = Join-Path $env:TEMP "neoforge-$version-installer.jar"
    Invoke-WebRequest -UseBasicParsing "https://maven.neoforged.net/releases/net/neoforged/neoforge/$version/neoforge-$version-installer.jar" -OutFile $installer
    $profiles = Join-Path $target 'launcher_profiles.json'
    if (-not (Test-Path $profiles)) { Set-Content $profiles '{"profiles":{}}' }
    & $java.Source -jar $installer --install-client $target | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host 'NeoForge установлен.' } else { Write-Host "NeoForge не встал: поставьте NeoForge $version в лаунчере." }
    Remove-Item $installer
} else {
    Write-Host "Java не найдена: в лаунчере выберите или установите NeoForge $version (Minecraft 1.21.1)."
}
Write-Host 'Готово. Запустите NeoForge 1.21.1 и зайдите на сервер 212.22.85.153'
