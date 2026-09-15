param(
    [switch]$Bump,              # tang versionCode (+1) va versionName (minor +1) truoc khi build
    [string]$VersionName = '',  # dat versionName cu the, vi du 2.2
    [switch]$SkipPush,          # chi build, khong push/tag/release
    [switch]$Draft              # tao release o dang nhap (draft)
)
$ErrorActionPreference = 'Stop'
# =====================================================================
# Phat hanh mot phien ban moi:
#   1. (tuy chon) tang so hieu phien ban trong version.properties
#   2. build APK release
#   3. commit + push code len GitHub
#   4. tao tag vX.Y va release kem file APK
#
# Chay:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\release.ps1 -Bump
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\release.ps1
#
# NOTE: giu file nay ASCII thuan (PowerShell 5.1 doc file khong BOM theo ANSI).
# =====================================================================

$root = 'D:\App\HKeyboard'
$versionFile = Join-Path $root 'version.properties'
$apkPath = Join-Path $root 'app\build\outputs\apk\release\Hkeyboard.apk'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

function Read-VersionProps {
    $props = @{}
    Get-Content $versionFile | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
        $kv = $_ -split '=', 2
        $props[$kv[0].Trim()] = $kv[1].Trim()
    }
    return $props
}

function Write-VersionProps([int]$code, [string]$name) {
    $text = @"
# Nguon duy nhat cho so hieu phien ban cua app.
#
# versionCode : so nguyen, BAT BUOC tang moi lan phat hanh (Android dung so nay
#               de so sanh phien ban khi cap nhat).
# versionName : chuoi hien thi cho nguoi dung, phai khop tag GitHub (v2.1 -> 2.1).
#
# Sau khi sua file nay, chay:  gradlew.bat assembleRelease
# Hoac dung script tu dong hoa:  powershell -File tools\release.ps1 -Bump

versionCode=$code
versionName=$name
"@
    [System.IO.File]::WriteAllText($versionFile, $text, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-Git([string[]]$gitArgs) {
    $out = & git -C $root @gitArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($gitArgs -join ' ') that bai:`n$out"
    }
    return $out
}

# ---------------------------------------------------------------------
# 1. So hieu phien ban
# ---------------------------------------------------------------------
$props = Read-VersionProps
$code = [int]$props['versionCode']
$name = $props['versionName']

if ($Bump) {
    $code = $code + 1
    $parts = $name.Split('.')
    $major = [int]$parts[0]
    $minor = if ($parts.Length -gt 1) { [int]$parts[1] } else { 0 }
    $name = "$major.$($minor + 1)"
}
if ($VersionName -ne '') {
    $name = $VersionName
}

if ($Bump -or $VersionName -ne '') {
    Write-VersionProps -code $code -name $name
    "Da dat phien ban: v$name (versionCode $code)"
}

$tag = "v$name"
"==> Phat hanh $tag (versionCode $code)"

# ---------------------------------------------------------------------
# 2. Build APK
# ---------------------------------------------------------------------
"==> Dang build APK..."
if (Test-Path $apkPath) { Remove-Item $apkPath -Force }
Push-Location $root
try {
    & cmd /c "gradlew.bat assembleRelease --console=plain 2>&1" | Out-String | Write-Verbose
} finally {
    Pop-Location
}
if (-not (Test-Path $apkPath)) {
    throw "Build that bai: khong thay $apkPath"
}
$apkSize = [Math]::Round((Get-Item $apkPath).Length / 1MB, 2)
"==> Da build xong: $apkPath ($apkSize MB)"

if ($SkipPush) {
    "==> -SkipPush: dung o buoc build."
    exit 0
}

# ---------------------------------------------------------------------
# 3. Commit + push
# ---------------------------------------------------------------------
Push-Location $root
try {
    $status = & git status --porcelain
    if ($status) {
        Invoke-Git @('add', '-A') | Out-Null
        Invoke-Git @('commit', '-m', "Phat hanh $tag") | Out-Null
        "==> Da commit thay doi"
    } else {
        "==> Khong co thay doi nao de commit"
    }

    $branch = (& git rev-parse --abbrev-ref HEAD).Trim()
    "==> Dang push len origin/$branch ..."
    Invoke-Git @('push', 'origin', $branch) | Out-Null

    # Xoa tag cu neu co roi tao lai
    & git tag -d $tag 2>$null | Out-Null
    & git push origin ":refs/tags/$tag" 2>$null | Out-Null
    Invoke-Git @('tag', '-a', $tag, '-m', "HKeyboard $tag") | Out-Null
    Invoke-Git @('push', 'origin', $tag) | Out-Null
    "==> Da tao va push tag $tag"
} finally {
    Pop-Location
}

# ---------------------------------------------------------------------
# 4. Tao release tren GitHub
# ---------------------------------------------------------------------
$notes = @"
## HKeyboard $tag

Build tu dong bang ``tools/release.ps1``.

**versionCode:** $code
**versionName:** $name
**Dung luong APK:** $apkSize MB

### Cai dat
1. Tai file ``Hkeyboard.apk`` o duoi.
2. Mo file de cai (cho phep "Cai dat tu nguon khong xac dinh" neu duoc hoi).
3. Neu ban dang dung ban cu, app se tu bao co ban moi o lan mo tiep theo.
"@
$notesFile = Join-Path $env:TEMP "hkeyboard-$tag-notes.md"
[System.IO.File]::WriteAllText($notesFile, $notes, [System.Text.UTF8Encoding]::new($false))

$ghArgs = @('release', 'create', $tag, $apkPath,
            '--title', "HKeyboard $tag",
            '--notes-file', $notesFile)
if ($Draft) { $ghArgs += '--draft' }

"==> Dang tao release tren GitHub..."
& gh @ghArgs
if ($LASTEXITCODE -ne 0) { throw 'tao release that bai' }

"==> XONG. Release $tag da duoc phat hanh."
& gh release view $tag --json url --jq .url
