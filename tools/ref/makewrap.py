#!/usr/bin/env python3
"""Ban Linux/macOS cua makewrap.ps1 + makeinputproc.ps1.

Sinh ra tu ma nguon UniKey 1.0.4:
  - ukengine_wrap.cpp  : ukengine.cpp + bang g_wrapMap + hook trong writeOutput
  - inputproc_wrap.cpp : inputproc.cpp + go wrapper trong keyCodeToEvent

Cach dung:
    python3 makewrap.py <thu-muc-src-cua-unikey> <thu-muc-dich>
"""
import sys
from pathlib import Path


def replace_once(text, old, new, what):
    if old not in text:
        raise SystemExit("khong tim thay diem chen: " + what)
    return text.replace(old, new, 1)


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    src = Path(sys.argv[1])
    dst = Path(sys.argv[2])

    # ---- ukengine.cpp ----
    t = (src / "ukengine" / "ukengine.cpp").read_text(encoding="latin-1")
    t = replace_once(
        t,
        "using namespace std;\n\n#define ENTER_CHAR 13\n",
        "using namespace std;\n\n#define ENTER_CHAR 13\n\n"
        "// id -> ky tu goc (0 = khong phai wrapper). Xem tools/ref/refmain.cpp.\n"
        "unsigned int g_wrapMap[4096] = {0};\n",
        "g_wrapMap")
    t = replace_once(
        t,
        "            stdChar = IsoToStdVnChar(m_buffer[i].keyCode);\n"
        "        }\n"
        "    \n"
        "        if (stdChar != INVALID_STD_CHAR)\n",
        "            stdChar = IsoToStdVnChar(m_buffer[i].keyCode);\n"
        "        }\n\n"
        "        if (m_buffer[i].vnSym == vnl_nonVnChar) {\n"
        "            unsigned int kc = m_buffer[i].keyCode;\n"
        "            if (kc >= 0xE000 && kc < 0xE000 + 4096 && g_wrapMap[kc - 0xE000])\n"
        "                stdChar = (StdVnChar)g_wrapMap[kc - 0xE000];\n"
        "        }\n"
        "    \n"
        "        if (stdChar != INVALID_STD_CHAR)\n",
        "writeOutput")
    (dst / "ukengine_wrap.cpp").write_text(t, encoding="latin-1")

    # ---- inputproc.cpp ----
    t = (src / "ukengine" / "inputproc.cpp").read_text(encoding="latin-1")
    t = replace_once(
        t,
        "    ev.keyCode = keyCode;\n"
        "    if (keyCode > 255) {\n"
        "        ev.evType = vneNormal;\n"
        "        ev.vnSym = IsoToVnLexi(keyCode);\n"
        "        ev.chType = (ev.vnSym == vnl_nonVnChar)? ukcNonVn : ukcVn;\n"
        "    }\n",
        "    // Wrapper: U+E000+id -> ky tu goc (xem tools/ref/makewrap.ps1).\n"
        "    if (keyCode >= 0xE000 && keyCode < 0xE000 + 4096 && g_wrapMap[keyCode - 0xE000])\n"
        "        keyCode = g_wrapMap[keyCode - 0xE000];\n\n"
        "    ev.keyCode = keyCode;\n"
        "    if (keyCode > 255) {\n"
        "        ev.evType = vneNormal;\n"
        "        ev.vnSym = vnl_nonVnChar;\n"
        "        ev.chType = ukcNonVn;\n"
        "    }\n",
        "keyCodeToEvent")
    t = replace_once(
        t,
        "//-------------------------------------------\nvoid SetupInputClassifierTable()\n",
        "// Wrapper map (dinh nghia trong ukengine_wrap.cpp)\n"
        "extern unsigned int g_wrapMap[];\n\n"
        "//-------------------------------------------\nvoid SetupInputClassifierTable()\n",
        "extern g_wrapMap")
    (dst / "inputproc_wrap.cpp").write_text(t, encoding="latin-1")
    print("Da ghi ukengine_wrap.cpp va inputproc_wrap.cpp vao", dst)


if __name__ == "__main__":
    main()
