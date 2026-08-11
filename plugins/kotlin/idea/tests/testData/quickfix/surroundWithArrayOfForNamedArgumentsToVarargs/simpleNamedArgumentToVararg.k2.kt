// "Surround with arrayOf(...)" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_FUNCTION_ERROR

fun foo(vararg s: String) {}

fun test() {
    foo(s = <caret>"value")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$SurroundWithArrayModCommandAction