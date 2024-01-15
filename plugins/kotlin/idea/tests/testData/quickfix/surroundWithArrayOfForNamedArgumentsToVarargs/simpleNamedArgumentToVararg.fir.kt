// "Surround with arrayOf(...)" "true"

fun foo(vararg s: String) {}

fun test() {
    foo(s = <caret>"value")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$applicator$1