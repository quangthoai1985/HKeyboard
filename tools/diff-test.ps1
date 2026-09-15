param(
    [string]$Corpus = 'D:\App\HKeyboard\tools\ref\corpus_big.txt',
    [int]$MaxReport = 40
)
$ErrorActionPreference = 'Stop'
# =====================================================================
# Differential test: run BOTH engines (the Java one in the app and the
# C++ one from UniKey 1.0.4) on the same key corpus and compare each line.
#
#   - C++  : tools/ref/ref.exe     (see tools/README.md)
#   - Java : tools/DiffDriver.java
#
# Both use the "wrapper" technique: every typed char is replaced by
# U+E000+id before being fed to the engine, then mapped back on output,
# so engine echo can never be confused with the typed input.
#
# NOTE: this file must stay pure ASCII - Windows PowerShell 5.1 reads a
# BOM-less file as ANSI.
#
# Run: powershell -NoProfile -ExecutionPolicy Bypass -File tools\diff-test.ps1
# =====================================================================
$root = 'D:\App\HKeyboard'
$refExe = Join-Path $root 'tools\ref\ref.exe'
$javaExe = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
$javacExe = 'C:\Program Files\Android\Android Studio\jbr\bin\javac.exe'
$buildDir = Join-Path $root 'tools\build'

if (-not (Test-Path $refExe)) {
    throw "Missing $refExe - run tools\ref\build.bat first."
}
if (-not (Test-Path $javaExe)) {
    throw "JDK not found at $javaExe"
}

# 1. Compile the Java side
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
& $javacExe -encoding UTF-8 -d $buildDir `
    (Join-Path $root 'app\src\main\java\com\hkeyboard\vni\UnikeyEngine.java') `
    (Join-Path $root 'tools\DiffDriver.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

# 2. Run the C++ side
$cppOut = Join-Path $root 'tools\ref\out_cpp.txt'
$javaOut = Join-Path $root 'tools\ref\out_java.txt'

$argsCpp = '/c ""' + $refExe + '" --stdin < "' + $Corpus + '" > "' + $cppOut + '" 2>nul"'
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = 'cmd.exe'
$psi.Arguments = $argsCpp
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
[void][System.Diagnostics.Process]::Start($psi).WaitForExit()

# 3. Run the Java side
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
$argsJava = '/c ""' + $javaExe + '" -cp "' + $buildDir + '" DiffDriver < "' + $Corpus + '" > "' + $javaOut + '" 2>nul"'
$psi2 = New-Object System.Diagnostics.ProcessStartInfo
$psi2.FileName = 'cmd.exe'
$psi2.Arguments = $argsJava
$psi2.UseShellExecute = $false
$psi2.CreateNoWindow = $true
[void][System.Diagnostics.Process]::Start($psi2).WaitForExit()

# 4. Compare
$cppLines = [System.IO.File]::ReadAllLines($cppOut, [System.Text.UTF8Encoding]::new($false))
$javaLines = [System.IO.File]::ReadAllLines($javaOut, [System.Text.UTF8Encoding]::new($false))
"CPP: $($cppLines.Count) lines | JAVA: $($javaLines.Count) lines"

$n = [Math]::Min($cppLines.Count, $javaLines.Count)
$diff = 0
$shown = 0
for ($i = 0; $i -lt $n; $i++) {
    $c = $cppLines[$i]
    $j = $javaLines[$i]
    if ($c -ne $j) {
        $diff++
        if ($shown -lt $MaxReport) {
            $shown++
            $cParts = $c -split "`t"
            $jParts = $j -split "`t"
            "DIFF #$i  keys='$($cParts[0])'  cpp='$($cParts[1])'  java='$($jParts[1])'"
        }
    }
}
if ($diff -eq 0) {
    "=== NO DIFFERENCES ($n lines) ==="
} else {
    "=== TOTAL DIFFS: $diff / $n ==="
}