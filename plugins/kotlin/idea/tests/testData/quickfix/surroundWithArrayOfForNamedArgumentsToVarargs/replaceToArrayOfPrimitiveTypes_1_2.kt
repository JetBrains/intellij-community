// "Surround with *intArrayOf(...)" "true"
// LANGUAGE_VERSION: 1.2
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(vararg s: Int) {}

fun test() {
    foo(s = <caret>1)
}
// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix