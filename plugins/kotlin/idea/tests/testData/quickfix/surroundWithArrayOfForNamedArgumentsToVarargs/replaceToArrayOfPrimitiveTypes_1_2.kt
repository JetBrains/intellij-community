// "Surround with *intArrayOf(...)" "true"
// LANGUAGE_VERSION: 1.2

fun foo(vararg s: Int) {}

fun test() {
    foo(s = <caret>1)
}
/* IGNORE_FIR */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix