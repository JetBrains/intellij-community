// "Surround with *arrayOf(...)" "true"
// LANGUAGE_VERSION: 1.2

fun anyFoo(vararg a: Any) {}

fun test() {
    anyFoo(a = intArr<caret>ayOf(1))
}
/* IGNORE_FIR */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix