// NEW_NAME: m
// RENAME: member
class A {
    val <caret>p: () -> String =  {""}
    fun m(s: String): String = ""

    fun n() {
        val pp = p()
        val mm = m("")
    }

}