#!/usr/bin/env bash
# =====================================================================
# Doi chieu vi sai tren Linux/macOS (ban tuong duong cua build.bat +
# diff-test.ps1): chay CA HAI bo may — Java trong app va C++ goc cua
# UniKey 1.0.4 — tren cung tap phim, so tung dong.
#
# Can: g++, python3, JDK 11+ (java, javac), curl, tar.
#
# Cach dung:
#     tools/diff-test.sh            # VNI + Telex, tuy chon mac dinh
#     tools/diff-test.sh --all      # them cac che do NOSPELL/MODERN/NOMARK/CAPS
# =====================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${HKEYBOARD_WORK:-$ROOT/tools/build}"
UNIKEY="$WORK/x-unikey-1.0.4"
mkdir -p "$WORK"

# 1. Ma nguon UniKey (LGPL v2, khong commit vao repo)
if [ ! -f "$UNIKEY/src/ukengine/ukengine.cpp" ]; then
    echo "Tai x-unikey-1.0.4 ..."
    curl -fsSL -o "$WORK/x-unikey-1.0.4.tar.bz2" \
        "https://sourceforge.net/projects/unikey/files/x-unikey/1.0.4/x-unikey-1.0.4.tar.bz2/download"
    tar -xjf "$WORK/x-unikey-1.0.4.tar.bz2" -C "$WORK"
fi
SRC="$UNIKEY/src"

# 2. Bien dich bo may C++ tham chieu
python3 "$ROOT/tools/ref/makewrap.py" "$SRC" "$WORK" >/dev/null
g++ -O1 -w -DUNIKEYHOOK \
    -I"$SRC/ukengine" -I"$SRC/vnconv" -I"$SRC/ukinterface" -I"$SRC/byteio" \
    "$ROOT/tools/ref/refmain.cpp" "$WORK/ukengine_wrap.cpp" "$WORK/inputproc_wrap.cpp" \
    "$SRC/vnconv/charset.cpp" "$SRC/vnconv/data.cpp" "$SRC/vnconv/convert.cpp" \
    "$SRC/vnconv/error.cpp" "$SRC/vnconv/pattern.cpp" "$SRC/byteio/byteio.cpp" \
    -o "$WORK/ref" 2>&1 | grep -v -e tempnam -e 'in function' || true

# 3. Bien dich phia Java
rm -rf "$WORK/classes"
javac -encoding UTF-8 -d "$WORK/classes" \
    "$ROOT/app/src/main/java/com/hkeyboard/vni/UnikeyEngine.java" \
    "$ROOT/tools/DiffDriver.java"

# 4. Corpus Telex (sinh lai duoc, khong commit)
TELEX_CORPUS="$ROOT/tools/ref/corpus_telex.txt"
if [ ! -f "$TELEX_CORPUS" ]; then
    java "$ROOT/tools/GenTelexCorpus.java" "$TELEX_CORPUS"
fi

FAILED=0
run() {  # run <nhan> <corpus> [BIEN=GIA_TRI ...]
    local label=$1 corpus=$2
    shift 2
    env "$@" "$WORK/ref" --stdin < "$corpus" > "$WORK/out_cpp.txt" 2>/dev/null
    env "$@" java -Dfile.encoding=UTF-8 -cp "$WORK/classes" DiffDriver \
        < "$corpus" > "$WORK/out_java.txt" 2>/dev/null
    local lines diffs
    lines=$(wc -l < "$WORK/out_cpp.txt")
    diffs=$(diff "$WORK/out_cpp.txt" "$WORK/out_java.txt" | grep -c '^<' || true)
    printf '%-16s %8s dong   %s khac biet\n' "$label" "$lines" "$diffs"
    if [ "$diffs" != "0" ]; then
        diff "$WORK/out_cpp.txt" "$WORK/out_java.txt" | head -20 || true
        FAILED=1
    fi
}

run "VNI"   "$ROOT/tools/ref/corpus_big.txt" UK_TELEX=0
run "TELEX" "$TELEX_CORPUS"                  UK_TELEX=1
if [ "${1:-}" = "--all" ]; then
    run "VNI NOSPELL"   "$ROOT/tools/ref/corpus_big.txt" UK_NOSPELL=1
    run "VNI MODERN"    "$ROOT/tools/ref/corpus_big.txt" UK_MODERN=1
    run "VNI NOMARK"    "$ROOT/tools/ref/corpus_big.txt" UK_NOMARK=1
    run "TELEX CAPS"    "$TELEX_CORPUS" UK_TELEX=1 UK_CAPS=1
    run "TELEX NOSPELL" "$TELEX_CORPUS" UK_TELEX=1 UK_NOSPELL=1
    run "TELEX MODERN"  "$TELEX_CORPUS" UK_TELEX=1 UK_MODERN=1
    run "TELEX NOMARK"  "$TELEX_CORPUS" UK_TELEX=1 UK_NOMARK=1
fi

if [ "$FAILED" = "0" ]; then
    echo "=== KHONG CO KHAC BIET ==="
else
    echo "=== CO KHAC BIET ==="
    exit 1
fi
