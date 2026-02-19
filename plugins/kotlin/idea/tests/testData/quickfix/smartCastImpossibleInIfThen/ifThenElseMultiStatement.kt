// "Replace 'if' expression with elvis expression" "false"
// ACTION: Add non-null asserted (x!!) call
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// ACTION: Introduce local variable
// DISABLE_ERRORS
class Test {
    var x: String? = ""

    fun test() {
        val i = if (x != null) {
            bar()
            <caret>x.length
        } else {
            0
        }
    }

    fun bar() {}
}