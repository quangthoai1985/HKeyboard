param(
    [string]$Version = '1.0.4',
    [string]$Destination = 'D:\App\HKeyboard\x-unikey-1.0.4'
)
$ErrorActionPreference = 'Stop'
# =====================================================================
# Tai ma nguon UniKey ve de lam "chuan doi chieu" cho tools/diff-test.ps1.
#
# Ma nguon nay thuoc du an UniKey (giay phep LGPL v2), KHONG phai mot phan
# cua HKeyboard, nen khong duoc commit vao repo nay.
#
# NOTE: giu file nay ASCII thuan (PowerShell 5.1 doc file khong BOM theo ANSI).
# =====================================================================

if (Test-Path (Join-Path $Destination 'src\ukengine\ukengine.cpp')) {
    "Da co ma nguon UniKey tai $Destination - khong tai lai."
    exit 0
}

$base = "https://sourceforge.net/projects/unikey/files/UniKey%20X%20Input%20Method/$Version"
$file = "unikey-$Version.tar.gz"
$tmp = Join-Path $env:TEMP $file
$url = "$base/$file/download"

"Tai $url ..."
try {
    Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing -TimeoutSec 120
} catch {
    throw "Khong tai duoc UniKey. Tai thu cong tu https://sourceforge.net/projects/unikey/files/ roi giai nen vao $Destination"
}

"Giai nen..."
$parent = Split-Path $Destination -Parent
if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }

# tar co san trong Windows 10 tro len.
& tar -xzf $tmp -C $parent
if ($LASTEXITCODE -ne 0) { throw 'giai nen that bai' }

$extracted = Join-Path $parent "unikey-$Version"
if (Test-Path $extracted) {
    if (Test-Path $Destination) { Remove-Item $Destination -Recurse -Force }
    Move-Item $extracted $Destination
}

if (Test-Path (Join-Path $Destination 'src\ukengine\ukengine.cpp')) {
    "Xong. Ma nguon UniKey nam tai $Destination"
} else {
    throw "Giai nen xong nhung khong thay src\ukengine\ukengine.cpp trong $Destination"
}
