# NeoForge 1.21.1: ставит/обновляет мод Mage's Legacy в папку mods TLauncher.
# Запуск: двойной клик по update-mages-legacy.bat (лежит рядом).
#   -Stable           взять последний стабильный релиз (v*), а не свежую dev-сборку
#   -VersionName "…"  имя версии в TLauncher (по умолчанию "Fabric 1.20.1")
#   -GameDir "…"      папка игры (по умолчанию %APPDATA%\.minecraft-codex-neoforge-1.21.1)
param(
    [switch]$Stable,
    [string]$VersionName = 'NeoForge 1.21.1',
    [string]$GameDir = (Join-Path $env:APPDATA '.minecraft-codex-neoforge-1.21.1')
)

$ErrorActionPreference = 'Stop'

# This installer exclusively owns a separate game directory.
$isolatedRoot = [IO.Path]::GetFullPath((Join-Path $env:APPDATA '.minecraft-codex-neoforge-1.21.1')).TrimEnd('\', '/')
if ([IO.Path]::GetFullPath($GameDir).TrimEnd('\', '/') -ine $isolatedRoot) {
    throw 'Use only %APPDATA%\.minecraft-codex-neoforge-1.21.1. The original Minecraft directory is protected.'
}
function Assert-IsolatedPath([string]$path) {
    $item = [IO.Path]::GetFullPath($path)
    while ($item) {
        if ((Test-Path -LiteralPath $item) -and ((Get-Item -LiteralPath $item -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Linked directories are not allowed: $item"
        }
        $parent = Split-Path -Parent $item
        if ($parent -eq $item) { break }
        $item = $parent
    }
}
Assert-IsolatedPath $GameDir

$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repo = 'stardely62-oss/mages-legacy-codex'
$Headers = @{ 'User-Agent' = 'mages-legacy-updater' }

function Say($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }

try {
    Say "== Mage's Legacy: обновление ==" Cyan

    # 1. Папка mods: отдельная у версии, если TLauncher так настроен, иначе общая
    $versionDir = Join-Path $GameDir "versions\$VersionName"
    if (-not (Test-Path $versionDir)) {
        throw "Не нашёл версию '$VersionName' в $GameDir\versions. Установи её в TLauncher и запусти один раз."
    }
    $mods = Join-Path $versionDir 'mods'
    if (-not (Test-Path $mods)) { $mods = Join-Path $GameDir 'mods' }
    Assert-IsolatedPath $mods
    New-Item -ItemType Directory -Force -Path $mods | Out-Null
    Say "Папка модов: $mods"

    if (Get-Process -Name javaw, java -ErrorAction SilentlyContinue) {
        throw "Close Minecraft before updating mods."
    }

    # Confirm a NeoForge 1.21.1 profile before touching any installed mods.
    $profile = Join-Path $versionDir "$VersionName.json"
    if (-not (Test-Path -LiteralPath $profile)) { throw "Missing launcher profile: $profile" }
    $profileText = Get-Content -LiteralPath $profile -Raw
    $loaderMatch = [regex]::Match($profileText, 'net\.neoforged:neoforge:(21\.1\.[0-9]+)')
    if (-not $loaderMatch.Success) { throw 'Choose a NeoForge 1.21.1 profile in the separate game directory.' }
    if ([version]$loaderMatch.Groups[1].Value -lt [version]'21.1.264') { throw 'NeoForge 21.1.264 or newer for Minecraft 1.21.1 is required.' }
    if (Get-ChildItem -LiteralPath $mods -Filter 'fabric-api-*.jar') { throw 'This folder contains Fabric mods. Use a clean NeoForge profile.' }

    # 4. Сам мод из GitHub Releases
    $api = if ($Stable) { "https://api.github.com/repos/$Repo/releases/latest" } else { "https://api.github.com/repos/$Repo/releases/tags/neoforge-dev" }
    $release = Invoke-RestMethod $api -Headers $Headers
    $asset = $release.assets | Where-Object { $_.name -like 'mages-legacy-*.jar' -and $_.name -notlike '*-sources*' } | Select-Object -First 1
    if (-not $asset) { throw "В релизе '$($release.name)' нет jar-файла мода." }

    if (Test-Path (Join-Path $mods $asset.name)) {
        Say "Мод уже последней версии: $($asset.name)" Green
    } else {
        $tmp = Join-Path $env:TEMP $asset.name
        Invoke-WebRequest $asset.browser_download_url -OutFile $tmp -Headers $Headers -UseBasicParsing
        Get-ChildItem $mods -Filter 'mages-legacy-*.jar' | Remove-Item -Force
        Move-Item $tmp (Join-Path $mods $asset.name) -Force
        Say "Установлен $($asset.name) ($($release.name))" Green
    }

    Say ""
    Say "Готово! Запускай в TLauncher версию '$VersionName'." Cyan
}
catch {
    Say ""
    Say "Ошибка: $($_.Exception.Message)" Red
    exit 1
}
