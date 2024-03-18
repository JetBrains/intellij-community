// NEW_NAME: m
// RENAME: member
class A {
    val <caret>p: String.() -> String =  {""}
    fun m(): String = ""

    fun n() {
        val pp = "".p()
        val mm = m()
    }

    fun String.n() {
        val pp = p()
        val mm = m()
    }
}

fun bar() {
    val mmBar = A().m()
    with(A()) {
        val ppppBar = "".p()
        val mmBar = m()
    }
}
// IGNORE_K1