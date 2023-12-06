// NEW_NAME: p
// RENAME: member
// SHOULD_FAIL_WITH: Property 'p' is already declared in class 'A'
class A {
    val p: () -> String =  {""}
    fun <caret>m(): String = ""

    fun n() {
        val pp = p()
        val mm = m()
    }

}