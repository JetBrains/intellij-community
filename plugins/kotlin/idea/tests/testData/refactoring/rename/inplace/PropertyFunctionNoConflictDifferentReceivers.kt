// NEW_NAME: p
// RENAME: member
class A {
    val p: () -> String =  {""}
    fun String.<caret>m(): String = ""

    fun n() {
        val pp = p()
        val mm = "".m()
    }

    fun String.n() {
        val pp = p()
        val mm = m()
    }
}

fun bar() {
    val ppBar = A().p()
    with(A()) {
        val ppppBar = p()
        val mmBar =  "".m()
    }
}
// IGNORE_K1