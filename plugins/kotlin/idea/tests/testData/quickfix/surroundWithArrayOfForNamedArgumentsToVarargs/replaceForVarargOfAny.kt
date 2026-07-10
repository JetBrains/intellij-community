// "Surround with *arrayOf(...)" "true"
// LANGUAGE_VERSION: 1.2
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun anyFoo(vararg a: Any) {}

fun test() {
    anyFoo(a = intArr<caret>ayOf(1))
}
// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix