// Reference harness: chay UniKey engine goc de doi chieu voi ban Java.
//
// KY THUAT WRAPPER: ca hai phia (C++ va Java) deu thay ky tu goc bang
// U+E000+id truoc khi dua vao engine, va doi nguoc lai o dau ra. Nho vay
// khong bi nham lan giua "ky tu nguoi dung go" va "ban ghi engine sinh ra"
// (vd go 'v' 'i' -> engine sinh ra "vi").
//
// Lan goi process() tra ve 0 thi ky tu duoc coi la da duoc commit ngay
// (giong caller that: he thong tu chen ky tu goc), nen harness tu chen.
//
// Cach dung:
//   ref.exe "chuoi phim"
//   ref.exe --stdin     -> moi dong la mot chuoi phim ('\b' = backspace)
//
// stdout moi dong: <raw>\t<field>\t<U+XXXX ...>

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

#include "ukengine.h"
#include "vnconv.h"

// ---- Stub cho CMacroTable (macroEnabled = 0 nen khong dung den) ----
void CMacroTable::init() { m_count = 0; m_memSize = MACRO_MEM_SIZE; m_occupied = 0; }
int CMacroTable::loadFromFile(const char *fname) { (void)fname; return 0; }
int CMacroTable::writeToFile(const char *fname) { (void)fname; return 0; }
const StdVnChar *CMacroTable::lookup(StdVnChar *key) { (void)key; return 0; }
const StdVnChar *CMacroTable::getKey(int idx) { (void)idx; return 0; }
const StdVnChar *CMacroTable::getText(int idx) { (void)idx; return 0; }
void CMacroTable::resetContent() { m_count = 0; m_occupied = 0; }
int CMacroTable::addItem(const char *item, int charset) { (void)item; (void)charset; return 0; }
int CMacroTable::addItem(const void *key, const void *text, int charset)
{ (void)key; (void)text; (void)charset; return 0; }
bool CMacroTable::readHeader(FILE *f, int &version) { (void)f; (void)version; return false; }
void CMacroTable::writeHeader(FILE *f) { (void)f; }

static UkSharedMem ctrl;
static UkEngine engine;

#define WRAP_BASE 0xE000
// Phai nho hon 8192 vi U+E000+8192 tran khoi pham vi char cua Java (0..65535).
#define WRAP_MAX  4096
extern unsigned int g_wrapMap[];   // dinh nghia trong ukengine_wrap.cpp
static int g_wrapCount = 0;

static void initEngine()
{
    memset(&ctrl, 0, sizeof(ctrl));
    g_wrapMap[0] = 0;
    ctrl.initialized = 1;
    ctrl.vietKey = 1;
    ctrl.charsetId = CONV_CHARSET_UNICODE;
    ctrl.options.freeMarking = 1;
    ctrl.options.modernStyle = 0;
    ctrl.options.macroEnabled = 0;
    ctrl.options.spellCheckEnabled = 1;
    ctrl.options.autoNonVnRestore = 0;
    // Cho phep doi tuy chon qua bien moi truong de kiem tra cac che do.
    const char *env;
    if ((env = getenv("UK_NOSPELL")) != NULL && env[0] == '1')
        ctrl.options.spellCheckEnabled = 0;
    if ((env = getenv("UK_MODERN")) != NULL && env[0] == '1')
        ctrl.options.modernStyle = 1;
    if ((env = getenv("UK_NOMARK")) != NULL && env[0] == '1')
        ctrl.options.freeMarking = 0;
    ctrl.input.init();
    ctrl.input.setIM(UkVni);
    ctrl.macStore.init();

    SetupUnikeyEngine();
    engine.setCtrlInfo(&ctrl);
    engine.reset();
}

#define FIELD_MAX 8192

static void printUtf8(unsigned int c)
{
    if (c < 0x80) putchar((int)c);
    else if (c < 0x800) {
        putchar(0xC0 | (c >> 6));
        putchar(0x80 | (c & 0x3F));
    } else if (c < 0x10000) {
        putchar(0xE0 | (c >> 12));
        putchar(0x80 | ((c >> 6) & 0x3F));
        putchar(0x80 | (c & 0x3F));
    } else {
        putchar(0xF0 | (c >> 18));
        putchar(0x80 | ((c >> 12) & 0x3F));
        putchar(0x80 | ((c >> 6) & 0x3F));
        putchar(0x80 | (c & 0x3F));
    }
}

static int collectOut(unsigned char *outBuf, int outSize, unsigned int *out)
{
    int n = 0;
    for (int i = 0; i + 1 < outSize && n < 2048; i += 2) {
        unsigned int c = (unsigned int)outBuf[i] | ((unsigned int)outBuf[i + 1] << 8);
        out[n++] = c;
    }
    return n;
}

// Tra ve so ky tu them vao field
static int doKey(unsigned int orig, unsigned int *field, int *fieldLen)
{
    if (g_wrapCount >= WRAP_MAX - 1) g_wrapCount = 0;
    g_wrapCount++;
    g_wrapMap[g_wrapCount] = orig;
    unsigned int wrapped = (unsigned int)(WRAP_BASE + g_wrapCount);

    unsigned char outBuf[8192];
    int backs = 0;
    int outSize = sizeof(outBuf);
    UkOutputType outType = UkCharOutput;
    int ret = engine.process(wrapped, backs, outBuf, outSize, outType);
    {
        UkKeyEvent dbg;
        ctrl.input.keyCodeToEvent(wrapped, dbg);
        fprintf(stderr, "[doKey] orig=%u wrapped=U+%04X evType=%d chType=%d vnSym=%d ret=%d backs=%d outSize=%d\n",
                orig, wrapped, dbg.evType, dbg.chType, dbg.vnSym, ret, backs, outSize);
    }

    if (backs > 0) {
        *fieldLen -= backs;
        if (*fieldLen < 0) *fieldLen = 0;
    }
    unsigned int emitted[2048];
    int n = collectOut(outBuf, outSize, emitted);
    for (int i = 0; i < n; i++) field[(*fieldLen)++] = emitted[i];
    // ret == 0: engine khong sinh output -> caller tu chen ky tu goc (giong
    // hanh vi that cua UniKey: he thong tu xu ly phim do).
    if (ret == 0) field[(*fieldLen)++] = wrapped;
    return ret;
}

static void doBackspace(unsigned int *field, int *fieldLen)
{
    unsigned char outBuf[8192];
    int backs = 0;
    int outSize = sizeof(outBuf);
    UkOutputType outType = UkCharOutput;
    int ret = engine.processBackspace(backs, outBuf, outSize, outType);
    if (backs > 0) {
        *fieldLen -= backs;
        if (*fieldLen < 0) *fieldLen = 0;
    }
    unsigned int emitted[2048];
    int n = collectOut(outBuf, outSize, emitted);
    for (int i = 0; i < n; i++) field[(*fieldLen)++] = emitted[i];
    // ret == 0 && backs == 0: engine khong lam gi -> caller tu xoa mot ky tu.
    // (Khi backs > 0 thi engine da bao so ky tu can xoa, khong xoa them.)
    if (ret == 0 && backs == 0 && *fieldLen > 0) (*fieldLen)--;
}

// Doi nguoc wrapper: U+E000+id -> ky tu goc
static unsigned int unwrap(unsigned int c)
{
    if (c >= WRAP_BASE && c < WRAP_BASE + WRAP_MAX) {
        unsigned int v = g_wrapMap[c - WRAP_BASE];
        if (v) return v;
    }
    return c;
}

static void reportField(const char *raw, unsigned int *field, int fieldLen)
{
    printf("%s\t", raw);
    for (int i = 0; i < fieldLen; i++) printUtf8(unwrap(field[i]));
    printf("\t");
    for (int i = 0; i < fieldLen; i++) {
        if (i > 0) putchar(' ');
        printf("U+%04X", unwrap(field[i]));
    }
}

int main(int argc, char **argv)
{
    initEngine();

    bool stdinMode = false;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--stdin") == 0) stdinMode = true;
    }

    static unsigned int field[FIELD_MAX];

    if (!stdinMode) {
        char raw[1024];
        int rl = 0;
        int fieldLen = 0;
        for (int a = 1; a < argc; a++) {
            for (const char *p = argv[a]; *p; p++) {
                doKey((unsigned int)(unsigned char)*p, field, &fieldLen);
                raw[rl++] = *p;
            }
        }
        raw[rl] = 0;
        reportField(raw, field, fieldLen);
        printf("\n");
        return 0;
    }

    char line[1024];
    while (fgets(line, sizeof(line), stdin)) {
        size_t L = strlen(line);
        while (L > 0 && (line[L - 1] == '\n' || line[L - 1] == '\r')) line[--L] = 0;

        engine.reset();
        g_wrapCount = 0;
        int fieldLen = 0;
        char raw[1024];
        int rl = 0;
        for (size_t i = 0; i < L; i++) {
            unsigned char c = (unsigned char)line[i];
            if (c == 8) {
                doBackspace(field, &fieldLen);
            } else {
                doKey(c, field, &fieldLen);
                raw[rl++] = (char)c;
            }
        }
        raw[rl] = 0;
        reportField(raw, field, fieldLen);
        printf("\n");
        fflush(stdout);
    }
    return 0;
}