package helium314.keyboard.event

import kotlin.test.Test
import kotlin.test.assertEquals

class TelexEngineTest {
    private fun t(raw: String, expected: String, modern: Boolean = false) =
        assertEquals(expected, TelexEngine.convert(raw, modern), raw)

    @Test fun basics() {
        t("a", "a"); t("aa", "â"); t("aw", "ă"); t("ee", "ê"); t("oo", "ô"); t("ow", "ơ"); t("uw", "ư"); t("dd", "đ")
        t("w", "ư"); t("Dd", "Đ"); t("DD", "Đ")
        t("as", "á"); t("af", "à"); t("ar", "ả"); t("ax", "ã"); t("aj", "ạ")
        t("Viet", "Viet"); t("vieetj", "việt"); t("Vieetj", "Việt"); t("VIEETJ", "VIỆT")
    }

    @Test fun words() {
        t("nguwowif", "người"); t("nguoiwf", "người"); t("duongw", "dương"); t("dduwowngf", "đường")
        t("tieengs", "tiếng"); t("Vieetj", "Việt"); t("quoocs", "quốc"); t("ngwx", "ngữ")
        t("toans", "toán"); t("hoaf", "hòa"); t("thuys", "thúy"); t("khoeer", "khoể")
        t("giaos", "giáo"); t("gis", "gí"); t("quas", "quá"); t("quyeenf", "quyền"); t("muas", "múa"); t("mias", "mía")
        t("khoais", "khoái"); t("nuwowcs", "nước"); t("truwowngf", "trường"); t("chuyeenj", "chuyện")
        t("hocj", "học"); t("didj", "đị")
    }

    @Test fun toneAnywhere() {
        t("toasn", "toán"); t("vieetj", "việt"); t("tuongw", "tương"); t("duocwj", "dược")
        t("ww", "w"); t("Ww", "W"); t("nhww", "nhw"); t("www", "ww")
        t("hoaf", "hòa"); t("hoaf", "hoà", modern = true); t("thuys", "thuý", modern = true); t("khoer", "khoẻ", modern = true)
    }

    @Test fun undoAndLiteral() {
        t("aaa", "aa"); t("aaaa", "aaa"); t("ass", "as"); t("asss", "ass"); t("aww", "aw"); t("ddd", "dd"); t("uww", "uw")
        t("zz", "zz"); t("az", "az"); t("aszz", "az")
    }

    @Test fun literal() {
        t("text", "text"); t("s", "s"); t("sa", "sa"); t("rs", "rs"); t("max", "mã"); t("xe", "xe")
        t("hello", "hello"); t("123", "123"); t("a1", "a1")
    }
}
