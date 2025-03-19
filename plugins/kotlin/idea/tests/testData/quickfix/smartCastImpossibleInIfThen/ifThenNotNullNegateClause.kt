// "Replace 'if' expression with safe access expression" "false"
// DISABLE_ERRORS
class Test {
    var x: String? = ""

    fun test() {
        val p: Int = if (x != null) <caret>x.length else 42
    }
}