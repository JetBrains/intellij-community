// "Surround with arrayOf(...)" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_FUNCTION_ERROR
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

class Foo<T>(vararg val p: T)

fun test() {
    Foo(p = 123<caret>)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$SurroundWithArrayModCommandAction