import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Sinh corpus chuoi phim TELEX de doi chieu ban Java voi UniKey goc
 * (tuong tu gen-corpus.ps1 cua VNI).
 *
 * Chay (JDK 11+, khong can bien dich truoc):
 *     java tools/GenTelexCorpus.java tools/ref/corpus_telex.txt
 *
 * Moi dong la mot chuoi phim; ky tu 0x08 la backspace.
 * Dung Random co dinh hat giong nen ket qua luon giong nhau.
 */
public class GenTelexCorpus {

    static final String[] ONSETS = {
            "", "b", "c", "ch", "d", "dd", "g", "gh", "gi", "h", "k", "kh", "l", "m",
            "n", "ng", "ngh", "nh", "p", "ph", "q", "qu", "r", "s", "t", "th", "tr",
            "v", "x", "D", "DD", "Dd", "C", "N", "Ng", "NgH", "TR", "Qu", "GI"};

    /** Nguyen am theo cach go Telex (dau mu/moc go ngay sau nguyen am). */
    static final String[] NUCLEI = {
            "a", "aw", "aa", "e", "ee", "i", "o", "oo", "ow", "u", "uw", "y",
            "uow", "uwow", "uwo", "uow", "uwa", "uoo", "owi", "awi", "aay", "aau",
            "uo", "oa", "oaw", "oe", "uy", "uyee", "uye", "ieu", "ieeu", "iee", "ie",
            "oai", "oay", "uoi", "uooi", "uowi", "uwowi", "uwu", "uwou", "uwowu",
            "ya", "yee", "yeeu", "ooi", "eeu", "uaa", "uee", "ua", "ui", "uwi", "ai", "ay",
            "ao", "au", "eo", "ia", "iu", "oi", "A", "AA", "AW", "OW", "UW", "UOW", "Uow", "EE"};

    static final String[] CODAS = {"", "c", "ch", "m", "n", "ng", "nh", "p", "t"};

    static final String[] TONES = {"", "s", "f", "r", "x", "j"};

    /** Nguyen am "tron" (chua go dau) + cac phim dau go SAU CUNG (free marking). */
    static final String[][] LATE_MARKS = {
            {"a", "a"}, {"a", "w"}, {"e", "e"}, {"o", "o"}, {"o", "w"}, {"u", "w"},
            {"uo", "w"}, {"uo", "ww"}, {"uo", "o"}, {"ie", "e"}, {"ye", "e"}, {"uye", "e"},
            {"oa", "w"}, {"ua", "a"}, {"ue", "e"}, {"uoi", "w"}, {"uou", "w"}, {"ieu", "e"},
            {"uu", "w"}, {"ua", "w"}, {"ui", "w"}, {"oi", "o"}, {"oi", "w"}, {"ai", "w"}};

    public static void main(String[] args) throws IOException {
        Path out = Paths.get(args.length > 0 ? args[0] : "tools/ref/corpus_telex.txt");
        Random rnd = new Random(20261015L);
        Set<String> lines = new LinkedHashSet<>();

        // 1. Am tiet: phu am dau + nguyen am + phu am cuoi + dau thanh (cuoi am tiet)
        for (String o : ONSETS) {
            for (String nu : NUCLEI) {
                for (String c : CODAS) {
                    for (String t : TONES) {
                        lines.add(o + nu + c + t);
                    }
                    // dau thanh go ngay sau nguyen am, truoc phu am cuoi
                    for (String t : new String[]{"s", "f", "j"}) {
                        lines.add(o + nu + t + c);
                    }
                    // go dau thanh hai lan (huy dau) va doi dau
                    lines.add(o + nu + c + "ss");
                    lines.add(o + nu + c + "sf");
                    lines.add(o + nu + c + "sz");
                }
            }
        }

        // 2. Dau mu/moc go SAU phu am cuoi (free marking) + dau thanh truoc/sau
        for (String o : ONSETS) {
            for (String[] lm : LATE_MARKS) {
                for (String c : CODAS) {
                    for (String t : TONES) {
                        lines.add(o + lm[0] + c + lm[1] + t);
                        if (!t.isEmpty()) {
                            lines.add(o + lm[0] + c + t + lm[1]);
                        }
                    }
                }
            }
        }

        // 3. Tu va cau tieng Viet go bang Telex
        String[] words = {
                "chaof banj", "Tooi laf nguwowif Vieetj Nam", "camr own banj raats nhieeuf",
                "dduwowngf", "nguwowif", "thuyeenf", "quyeenf", "Nguyeenx", "giuwa", "Howp",
                "tieengs Vieetj", "chuwowng trinhf", "ddawcj bieetj", "khoer manhj",
                "hoaf binhf", "thur ddoo", "xax hooij", "giaos ducj", "y tees",
                "coong ngheej", "phats trieenr", "kinh tees", "vawn hoas", "lichj sur",
                "toans hocj", "vaatj lys", "hoas hocj", "sinh hocj", "nguwowif dungf",
                "mays tinhs", "ddieenj thoaij", "banf phims", "Haf Nooij",
                "thanhf phoos Hoof Chis Minh", "Vieetj Nam ddaats nuwowcs con nguwowif",
                "awn cowm", "uoongs nuwowcs", "ddi hocj", "lamf vieecj", "nghir pheps",
                "xawng daauf", "giao thoong", "tai nanj", "baor hieemr", "kees toans",
                "ngaan hangf", "tieenf teej", "thuwowng", "thuowng", "truwowngf", "truowngf",
                "nuwowcs", "nuowcs", "hwowngs", "huowngs", "khuyur tay", "khuyeen",
                "quaan", "quoocs", "gioongs", "giuwowngf", "gif", "gir", "gix", "giaf",
                "ddoongf", "DDoongf", "dDoongf", "Dd", "dd", "ddd", "dddd", "ddz",
                "hoaf", "hoar", "khoer", "thuys", "tuyr", "loaf", "quyas",
                "muwowif", "mwowif", "tuwj", "tuwf", "tuwr", "tuw", "tw", "w", "W",
                "uw", "Uw", "UW", "ow", "Ow", "aw", "AW", "ww", "WW", "www", "wwww",
                "uww", "oww", "aww", "uoww", "uowww", "nguowiw", "ngwowif",
                "chuwowng", "chwowng", "Chwowng", "cwf", "nwx", "trw", "trwowngf",
                "tieeng", "tieeengs", "tieengss", "aaa", "aaaa", "eee", "ooo", "oooo",
                "taaa", "too", "tooo", "toooo", "cuar", "cuaar", "cuoocs", "uoo", "uooo",
                "VIEETJ NAM", "NGUWOWIF", "TRUWOWNGF", "DDAATS NUWOWCS", "QUYEENF",
                "Vieejt", "Vieetj", "Vietej", "Vietje", "Viejet", "vieej", "vieetjj",
                "Trowif owi", "trowif", "owif", "ow owi", "uwow", "uwowi", "uwowu",
                "ruwowuj", "ruouwj", "khuyuar", "khuyur", "quys", "quysy", "quyr"};
        for (String w : words) {
            lines.add(w);
        }

        // 4. Tu tieng Anh / ky hieu / ma nguon — chua nhieu phim dau cua Telex
        String[] others = {
                "hello", "world", "window", "windows", "awesome", "class", "error", "success",
                "facebook", "google", "zoo", "book", "coffee", "keep", "free", "address",
                "word", "twitter", "JSON", "Wi-Fi", "law", "new", "raw", "show", "w3c",
                "www.google.com", "user@mail.com", "HKeyboard", "Unikey", "Android", "Java",
                "GitHub", "java8", "x2", "var x = 1;", "print(\"hi\")", "http://example.com",
                "file_name.txt", "3.14159", "0912345678", "100%", "(a+b)*c", "<html>",
                "#hashtag", "jazz", "fizz", "buzz", "fuzzy", "jump", "fox", "box", "six",
                "xerox", "taxi", "sex", "mix", "fix", "jeans", "ski", "rest", "west", "east",
                "news", "saw", "jaw", "few", "dew", "sew", "wow", "WOW", "Wow", "owl",
                "power", "tower", "flower", "follow", "yellow", "swiss", "miss", "kiss",
                "boss", "less", "mass", "pass", "toss", "jj", "ff", "ss", "rr", "xx", "zz",
                "asdfgh", "qwerty", "zxcvbn", "fghjkl", "wxyz", "jjj", "fff", "zzz", "sss",
                "deadline", "feedback", "free", "seen", "moon", "door", "food", "good",
                "sweet", "queen", "dddd", "added", "odd", "add"};
        for (String w : others) {
            lines.add(w);
        }

        // 5. Phim ngoac: [ -> o+, ] -> u+, { -> O+, } -> U+
        String[] brackets = {
                "[", "]", "{", "}", "[[", "]]", "{{", "}}", "[[[", "]]]",
                "t[", "t]", "th[", "tr]", "ng]", "ng[", "a[", "a]", "[a", "]a", "[i", "]a",
                "t[i", "t]a", "ng]owif", "ng][", "t][", "t[]", "arr[0]", "x[i] = y[j];",
                "if (a) { b; }", "{}", "[]", "m[n", "m]n", "T[", "T]", "T{", "T}", "[s", "]f",
                "h[p", "t]ng", "tr]ngf", "ch]ng", "ch][ng", "c]a", "m]a", "m]af", "l]ow",
                "[abc]", "{x}", "[w]", "w[", "w]", "]w", "[w", "u[", "u]", "q]", "qu]", "g]", "gi]"};
        for (String b : brackets) {
            lines.add(b);
        }

        // 6. Backspace (0x08)
        char bs = 8;
        String[] bsBase = {"as", "vieetj", "dduwowngf", "a s", "Tooi", "nguwowif", "ass", "ddd",
                "uw", "w", "ww", "tw", "uow", "[", "]", "t[", "aa", "aaa", "thuowng", "Dd"};
        for (String w : bsBase) {
            lines.add(w + bs);
            lines.add(w + bs + bs);
            lines.add(w + bs + "x");
            lines.add(w + bs + "w");
            lines.add(w + bs + "s");
        }

        // 7. Ngau nhien tu bang chu cai + so + dau (nhieu phim Telex)
        String alpha = "abcdefghijklmnopqrstuvwxyzwwwsfrxjzaeodd";
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZW";
        String punct = " .,@-_/[]{}";
        String digits = "0123456789";
        for (int i = 0; i < 20000; i++) {
            int len = 2 + rnd.nextInt(11);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < len; j++) {
                int r = rnd.nextInt(100);
                if (r < 70) {
                    sb.append(alpha.charAt(rnd.nextInt(alpha.length())));
                } else if (r < 80) {
                    sb.append(upper.charAt(rnd.nextInt(upper.length())));
                } else if (r < 88) {
                    sb.append(digits.charAt(rnd.nextInt(digits.length())));
                } else if (r < 97) {
                    sb.append(punct.charAt(rnd.nextInt(punct.length())));
                } else {
                    sb.append(bs);
                }
            }
            lines.add(sb.toString());
        }

        // 8. Ngau nhien tu am tiet tieng Viet (1-4 am tiet), doi ngau nhien chu HOA
        for (int i = 0; i < 20000; i++) {
            int n = 1 + rnd.nextInt(4);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) {
                    sb.append(' ');
                }
                StringBuilder syl = new StringBuilder();
                syl.append(ONSETS[rnd.nextInt(ONSETS.length)]);
                if (rnd.nextInt(3) == 0) {
                    String[] lm = LATE_MARKS[rnd.nextInt(LATE_MARKS.length)];
                    syl.append(lm[0]).append(CODAS[rnd.nextInt(CODAS.length)]);
                    String t = TONES[rnd.nextInt(TONES.length)];
                    if (rnd.nextBoolean()) {
                        syl.append(lm[1]).append(t);
                    } else {
                        syl.append(t).append(lm[1]);
                    }
                } else {
                    syl.append(NUCLEI[rnd.nextInt(NUCLEI.length)]);
                    syl.append(CODAS[rnd.nextInt(CODAS.length)]);
                    syl.append(TONES[rnd.nextInt(TONES.length)]);
                }
                if (rnd.nextInt(8) == 0) {
                    // thinh thoang go them mot phim dau/huy dau
                    syl.append("swfrxjzaeod".charAt(rnd.nextInt(11)));
                }
                String s = syl.toString();
                if (rnd.nextInt(6) == 0) {
                    s = s.toUpperCase();
                } else if (rnd.nextInt(6) == 0 && !s.isEmpty()) {
                    s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
                }
                sb.append(s);
            }
            lines.add(sb.toString());
        }

        List<String> clean = new ArrayList<>();
        for (String s : lines) {
            if (!s.isEmpty() && s.indexOf('\n') < 0 && s.indexOf('\r') < 0) {
                clean.add(s);
            }
        }
        StringBuilder text = new StringBuilder();
        for (String s : clean) {
            text.append(s).append('\n');
        }
        Files.write(out, text.toString().getBytes(StandardCharsets.US_ASCII));
        System.out.println("Da ghi " + clean.size() + " dong vao " + out);
    }
}
