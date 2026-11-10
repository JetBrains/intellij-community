class Holder(val user: String)

fun wrap<caret>Local(p: Holder): String {
    val s = Holder("inner")
    return p.user + s.user
}

fun caller(s: Holder): String = wrapLocal(p = s)
