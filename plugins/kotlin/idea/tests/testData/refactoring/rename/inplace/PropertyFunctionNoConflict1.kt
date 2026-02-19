// NEW_NAME: p
// RENAME: member
class A {
    val p: () -> String =  {""}
    fun <caret>m(s: String): String = ""

    fun n() {
        val pp = p()
        val mm = m("")
    }

}