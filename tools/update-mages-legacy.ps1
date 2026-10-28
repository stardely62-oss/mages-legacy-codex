# Ставит/обновляет мод Mage's Legacy и подходящий Fabric API в папку mods TLauncher.
# Запуск: двойной клик по update-mages-legacy.bat (лежит рядом).
#   -Stable           взять последний стабильный релиз (v*), а не свежую dev-сборку
#   -VersionName "…"  имя версии в TLauncher (по умолчанию "Fabric 1.20.1")
#   -GameDir "…"      папка игры (по умолчанию %APPDATA%\.minecraft-codex)
param(
    [switch]$Stable,
    [string]$VersionName = 'Fabric 1.20.1',
    [string]$GameDir = (Join-Path $env:APPDATA '.minecraft-codex')
)

$ErrorActionPreference = 'Stop'

# This installer exclusively owns a separate game directory.
$isolatedRoot = [IO.Path]::GetFullPath((Join-Path $env:APPDATA '.minecraft-codex')).TrimEnd('\', '/')
if ([IO.Path]::GetFullPath($GameDir).TrimEnd('\', '/') -ine $isolatedRoot) {
    throw 'Use only %APPDATA%\.minecraft-codex. The original Minecraft directory is protected.'
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
        Say "Похоже, Minecraft запущен. Закрой игру, иначе старые файлы не удалятся." Yellow
    }

    # 2. Версия Fabric Loader у этой версии TLauncher -> какой Fabric API подходит
    $loader = $null
    $json = Get-ChildItem $versionDir -Filter '*.json' | Select-Object -First 1
    if ($json) {
        $m = [regex]::Match((Get-Content $json.FullName -Raw), 'net\.fabricmc:fabric-loader:([0-9][0-9.]*)')
        if ($m.Success) { $loader = [version]$m.Groups[1].Value }
    }
    if (-not $loader) {
        Say "Не смог определить версию Fabric Loader, беру Fabric API для самого старого загрузчика." Yellow
        $loader = [version]'0.14.21'
    }
    if ($loader -lt [version]'0.14.21') {
        throw "Fabric Loader $loader слишком старый, нужен 0.14.21 или новее. Выбери в TLauncher версию Fabric поновее."
    }
    if ($loader -ge [version]'0.16.10') { $fapi = '0.92.12+1.20.1' }
    elseif ($loader -ge [version]'0.15.6') { $fapi = '0.92.2+1.20.1' }
    else { $fapi = '0.91.0+1.20.1' }
    Say "Fabric Loader $loader -> Fabric API $fapi"

    # 3. Fabric API с Modrinth (только если нужной версии ещё нет)
    $fapiName = "fabric-api-$fapi.jar"
    if (Test-Path (Join-Path $mods $fapiName)) {
        Say "Fabric API уже стоит."
    } else {
        $query = 'game_versions=' + [uri]::EscapeDataString('["1.20.1"]') + '&loaders=' + [uri]::EscapeDataString('["fabric"]')
        $versions = Invoke-RestMethod "https://api.modrinth.com/v2/project/fabric-api/version?$query" -Headers $Headers
        $v = $versions | Where-Object { $_.version_number -eq $fapi } | Select-Object -First 1
        if (-not $v) { throw "Fabric API $fapi не найден на Modrinth." }
        $file = $v.files | Where-Object { $_.primary } | Select-Object -First 1
        if (-not $file) { $file = $v.files[0] }
        Get-ChildItem $mods -Filter 'fabric-api-*.jar' | Remove-Item -Force
        Invoke-WebRequest $file.url -OutFile (Join-Path $mods $fapiName) -Headers $Headers -UseBasicParsing
        Say "Fabric API $fapi установлен." Green
    }

    # 4. Сам мод из GitHub Releases
    $api = if ($Stable) { "https://api.github.com/repos/$Repo/releases/latest" } else { "https://api.github.com/repos/$Repo/releases/tags/dev" }
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
