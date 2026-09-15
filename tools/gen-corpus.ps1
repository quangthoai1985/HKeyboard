$ErrorActionPreference = 'Stop'
# Sinh corpus lon de so sanh vi sai ban Java voi UniKey goc.
# Moi dong la mot chuoi phim; '\b' la backspace.
$out = 'D:\App\HKeyboard\tools\ref\corpus_big.txt'
$rnd = New-Object System.Random 20260101

$lines = New-Object System.Collections.Generic.List[string]

# 1. Am tiet tieng Viet voi moi kieu go dau (VNI)
$onsets = @('', 'b', 'c', 'ch', 'd', 'd9', 'g', 'gh', 'gi', 'h', 'k', 'kh', 'l', 'm',
            'n', 'ng', 'ngh', 'nh', 'p', 'ph', 'q', 'qu', 'r', 's', 't', 'th', 'tr',
            'v', 'x', 'D', 'D9', 'C', 'N', 'Ng', 'NgH')
$nuclei = @('a', 'a8', 'a6', 'e', 'e6', 'i', 'o', 'o6', 'o7', 'u', 'u7', 'y',
            'u7o7', 'u7a', 'u7o6', 'o7i', 'a8i', 'a6y', 'uo', 'oa', 'oe', 'uy')
$codas  = @('', 'c', 'ch', 'm', 'n', 'ng', 'nh', 'p', 't')
$tones  = @('', '1', '2', '3', '4', '5')

foreach ($o in $onsets) {
    foreach ($nu in $nuclei) {
        foreach ($c in $codas) {
            foreach ($t in $tones) {
                $lines.Add("$o$nu$c$t")
            }
            # truong hop dau dat truoc nguyen am
            foreach ($t in @('1','2','5')) {
                $lines.Add("$o$t$nu$c")
            }
        }
    }
}

# 2. Tu khoa hoc / cau tieng Viet thong dung
$words = @(
    'chao2 ban5', 'To6i la2 ngu7o7i Vie65t Nam', 'ca3m o7n ba5n ra61t nhie62u',
    'd9u7o7ng', 'ngu7o7i', 'thuye63n', 'quye63n', 'Nguye63n', 'giu7a', 'Ho7p',
    'tie61ng Vie65t', 'chu7o7ng tri2nh', 'd9a85c bie65t', 'kho3e ma1nh',
    'hoa2 bi2nh', 'thu73 d9o6', 'xa4 ho65i', 'gia1o du5c', 'y te61',
    'co6ng nghe65', 'pha1t trie63n', 'kinh te61', 'va8n ho1a', 'li5ch su73',
    'toa1n ho5c', 'va65t ly1', 'ho1a ho5c', 'sinh ho5c', 'ngu7o7i du2ng',
    'ma1y ti1nh', 'd9ie65n thoa5i', 'ba2n phím', 'chu7o7ng tri2nh ma1y ti1nh',
    'd9u7o7ng pho61', 'ha2 no65i', 'tha2nh pho61 ho6 chi1 minh',
    'Vie65t Nam d9a61t nu7o7c con ngu7o7i', 'a8n co7m', 'u7o61ng nu7o7c',
    'd9i ho5c', 'la2m vie65c', 'nghi3 phe1p', 'xa8ng da62u', 'giao tho6ng',
    'tai7 na5n', 'ba3o hie63m', 'ke61 toa1n', 'nga6n ha2ng', 'tie62n te65'
)
foreach ($w in $words) { $lines.Add($w) }

# 3. Tu khong phai tieng Viet / hon hop / ky tu d9a85c bie65t
$others = @(
    'hello', 'world', 'HKeyboard', 'Unikey', 'Android', 'Java', 'GitHub', 'abc123',
    'java8', 'x2', 'a@b.com', 'user_name', 'var x = 1;', 'print("hi")',
    'http://example.com', 'C:\Users\test', 'file_name.txt', '3.14159',
    '0912345678', '2026-01-01', '100%', '(a+b)*c', '<html>', '#hashtag',
    'toi6', 'to6i', 'a11', 'a22', 'a33', 'a44', 'a55', 'a66', 'a77', 'a88', 'a99',
    'd99', 'e66', 'o77', 'u77', 'a68', 'a86', 'o67', 'o76',
    'asdfgh', 'qwerty', 'zxcvbn', 'fghjkl', 'wxyz', 'jjj', 'fff', 'www', 'zzz'
)
foreach ($w in $others) { $lines.Add($w) }

# 4. Backspace (ky tu backspace that = 0x08)
$bs = [char]8
foreach ($w in @('a1', 'vie65t', 'd9u7o7ng', 'a 1', 'To6i', 'ngu7o7i', 'a11', 'd99')) {
    $lines.Add("$w$bs")
    $lines.Add("$w$bs$bs")
    $lines.Add("$w${bs}x")
}

# 5. Ngau nhien tu bang chu cai + so + dau
$alpha = 'abcdefghijklmnopqrstuvwxyz'.ToCharArray()
$digits = '0123456789'.ToCharArray()
$punct = ' .,@-_/'.ToCharArray()
for ($i = 0; $i -lt 3000; $i++) {
    $len = $rnd.Next(2, 12)
    $sb = New-Object System.Text.StringBuilder
    for ($j = 0; $j -lt $len; $j++) {
        $r = $rnd.Next(100)
        if ($r -lt 62) { [void]$sb.Append($alpha[$rnd.Next($alpha.Length)]) }
        elseif ($r -lt 85) { [void]$sb.Append($digits[$rnd.Next($digits.Length)]) }
        else { [void]$sb.Append($punct[$rnd.Next($punct.Length)]) }
    }
    $lines.Add($sb.ToString())
}

# 6. Ngau nhien tu am tiet tieng Viet
for ($i = 0; $i -lt 3000; $i++) {
    $n = $rnd.Next(1, 4)
    $sb = New-Object System.Text.StringBuilder
    for ($j = 0; $j -lt $n; $j++) {
        if ($j -gt 0) { [void]$sb.Append(' ') }
        [void]$sb.Append($onsets[$rnd.Next($onsets.Length)])
        [void]$sb.Append($nuclei[$rnd.Next($nuclei.Length)])
        [void]$sb.Append($codas[$rnd.Next($codas.Length)])
        [void]$sb.Append($tones[$rnd.Next($tones.Length)])
    }
    $lines.Add($sb.ToString())
}

# Loai bo dong trong va dong chua ky tu xuong dong
$clean = $lines | Where-Object { $_ -ne '' -and $_ -notmatch '[\r\n]' }
$text = ($clean -join "`n") + "`n"
[System.IO.File]::WriteAllText($out, $text, [System.Text.ASCIIEncoding]::new())
"Da ghi $($clean.Count) dong vao $out"