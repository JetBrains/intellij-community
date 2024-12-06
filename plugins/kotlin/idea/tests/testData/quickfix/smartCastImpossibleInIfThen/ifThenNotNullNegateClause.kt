// "Replace 'if' expression with safe access expression" "false"
// DISABLE-ERRORS
class Test {
    var x: String? = ""

    fun test() {
        val p: Int = if (x != null) <caret>x.length else 42
    }
}