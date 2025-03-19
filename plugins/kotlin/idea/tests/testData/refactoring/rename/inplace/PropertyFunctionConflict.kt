// NEW_NAME: m
// RENAME: member
// SHOULD_FAIL_WITH: Function 'm' is already declared in class 'A'
class A {
    val <caret>p: () -> String =  {""}
    fun m(): String = ""

    fun n() {
        val pp = p()
        val mm = m()
    }
}