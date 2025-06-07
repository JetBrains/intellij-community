// "Surround with *arrayOf(...)" "true"
// LANGUAGE_VERSION: 1.2

fun foo(vararg s: String) {}

fun test() {
    foo(s = <caret>"value")
}
// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix