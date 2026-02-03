// NEW_NAME: p
// RENAME: member
class A {
    val p: (String) -> String =  {""}
    fun <caret>m(): String = ""

    fun n() {
        val pp = p("")
        val mm = m()
    }

}