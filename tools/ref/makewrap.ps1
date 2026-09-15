$ErrorActionPreference = 'Stop'
# Tao ban sao ukengine.cpp co gan hook wrapper cho writeOutput.
# Harness se thay ky tu goc bang U+E000+id truoc khi dua vao engine, nho do
# phan biet duoc ky tu nguoi dung go va ban ghi engine sinh ra.
$src = 'D:\App\HKeyboard\x-unikey-1.0.4\src\ukengine\ukengine.cpp'
$dst = 'D:\App\HKeyboard\tools\ref\ukengine_wrap.cpp'
$t = [System.IO.File]::ReadAllText($src, [System.Text.Encoding]::GetEncoding(1252))

$a0 = @"
using namespace std;

#define ENTER_CHAR 13
"@
$b0 = @"
using namespace std;

#define ENTER_CHAR 13

// id -> ky tu goc (0 = khong phai wrapper). Xem tools/ref/refmain.cpp.
unsigned int g_wrapMap[4096] = {0};
"@
if (-not $t.Contains($a0)) { throw 'khong tim thay diem chen 0' }
$t = $t.Replace($a0, $b0)

$a1 = @"
        else {
            stdChar = IsoToStdVnChar(m_buffer[i].keyCode);
        }
    
        if (stdChar != INVALID_STD_CHAR)
            ret = pCharset->putChar(os, stdChar, bytesWritten);
"@
$b1 = @"
        else {
            stdChar = IsoToStdVnChar(m_buffer[i].keyCode);
        }

        if (m_buffer[i].vnSym == vnl_nonVnChar) {
            unsigned int kc = m_buffer[i].keyCode;
            if (kc >= 0xE000 && kc < 0xE000 + 4096 && g_wrapMap[kc - 0xE000])
                stdChar = (StdVnChar)g_wrapMap[kc - 0xE000];
        }
    
        if (stdChar != INVALID_STD_CHAR)
            ret = pCharset->putChar(os, stdChar, bytesWritten);
"@
if (-not $t.Contains($a1)) { throw 'khong tim thay diem chen writeOutput' }
$t = $t.Replace($a1, $b1)

# Trace: cho biet processAppend ket thuc o dau
$a2 = @"
    case ukcNonVn:
        {
            if (m_pCtrl->vietKey && m_pCtrl->charsetId == CONV_CHARSET_VIQR && checkEscapeVIQR(ev))
                return 1;
"@
$b2 = @"
    case ukcNonVn:
        {
            if (m_pCtrl->vietKey && m_pCtrl->charsetId == CONV_CHARSET_VIQR && checkEscapeVIQR(ev))
                return 1;
"@
if (-not $t.Contains($a2)) { throw 'khong tim thay diem chen ukcNonVn' }

$a3 = @"
            entry.caps = (entry.vnSym != ev.vnSym);
            if (!m_pCtrl->vietKey || m_pCtrl->charsetId != CONV_CHARSET_UNI_CSTRING)
                return 0;
            markChange(m_current);
            return 1;
        }
    case ukcVn:
"@
$b3 = @"
            entry.caps = (entry.vnSym != ev.vnSym);
            fprintf(stderr, "[ukcNonVn] key=%u vietKey=%d charsetId=%d CSTRING=%d cap=%d\n",
                    ev.keyCode, m_pCtrl->vietKey, m_pCtrl->charsetId, (int)CONV_CHARSET_UNI_CSTRING, m_current);
            if (!m_pCtrl->vietKey || m_pCtrl->charsetId != CONV_CHARSET_UNI_CSTRING)
                return 0;
            markChange(m_current);
            return 1;
        }
    case ukcVn:
"@
if (-not $t.Contains($a3)) { throw 'khong tim thay diem chen ukcNonVn-tail' }
$t = $t.Replace($a3, $b3)

# Trace writeOutput
$a4 = @"
    for (i = m_changePos; i <= m_current; i++) {
        if (m_buffer[i].vnSym != vnl_nonVnChar) {
"@
$b4 = @"
    fprintf(stderr, "[writeOutput] changePos=%d current=%d charset=%d\n", m_changePos, m_current, m_pCtrl->charsetId);
    for (i = m_changePos; i <= m_current; i++) {
        if (m_buffer[i].vnSym != vnl_nonVnChar) {
"@
if (-not $t.Contains($a4)) { throw 'khong tim thay diem chen writeOutput-header' }
$t = $t.Replace($a4, $b4)

[System.IO.File]::WriteAllText($dst, $t, [System.Text.Encoding]::GetEncoding(1252))
"Da ghi $dst ($($t.Length) ky tu)"
