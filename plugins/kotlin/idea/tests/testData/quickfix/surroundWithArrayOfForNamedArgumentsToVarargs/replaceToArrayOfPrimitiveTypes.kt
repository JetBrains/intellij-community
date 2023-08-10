// "Surround with *intArrayOf(...)" "true"

fun foo(vararg s: Int) {}

fun test() {
    foo(s = <caret>1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix