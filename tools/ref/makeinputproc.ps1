$ErrorActionPreference = 'Stop'
# Tao ban sao inputproc.cpp co guard cho keyCode > 255 (tran bang IsoVnLexiMap).
# Chi anh huong ban tham chieu dung de doi chieu, khong dung cho san pham.
$src = 'D:\App\HKeyboard\x-unikey-1.0.4\src\ukengine\inputproc.cpp'
$dst = 'D:\App\HKeyboard\tools\ref\inputproc_wrap.cpp'
$t = [System.IO.File]::ReadAllText($src, [System.Text.Encoding]::GetEncoding(1252))

$a = @"
    ev.keyCode = keyCode;
    if (keyCode > 255) {
        ev.evType = vneNormal;
        ev.vnSym = IsoToVnLexi(keyCode);
        ev.chType = (ev.vnSym == vnl_nonVnChar)? ukcNonVn : ukcVn;
    }
"@
$b = @"
    // Wrapper: U+E000+id -> ky tu goc (xem tools/ref/makewrap.ps1).
    if (keyCode >= 0xE000 && keyCode < 0xE000 + 4096 && g_wrapMap[keyCode - 0xE000])
        keyCode = g_wrapMap[keyCode - 0xE000];

    ev.keyCode = keyCode;
    if (keyCode > 255) {
        ev.evType = vneNormal;
        ev.vnSym = vnl_nonVnChar;
        ev.chType = ukcNonVn;
    }
"@
if (-not $t.Contains($a)) { throw 'khong tim thay diem chen keyCodeToEvent' }
$t = $t.Replace($a, $b)

$a2 = @"
//-------------------------------------------
void SetupInputClassifierTable()
"@
$b2 = @"
// Wrapper map (dinh nghia trong ukengine_wrap.cpp)
extern unsigned int g_wrapMap[];

//-------------------------------------------
void SetupInputClassifierTable()
"@
if (-not $t.Contains($a2)) { throw 'khong tim thay diem chen extern' }
$t = $t.Replace($a2, $b2)

[System.IO.File]::WriteAllText($dst, $t, [System.Text.Encoding]::GetEncoding(1252))
"Da ghi $dst ($($t.Length) ky tu)"
