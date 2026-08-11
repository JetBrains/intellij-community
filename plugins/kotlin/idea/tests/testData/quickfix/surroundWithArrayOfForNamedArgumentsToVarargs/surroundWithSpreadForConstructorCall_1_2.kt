// "Surround with *arrayOf(...)" "true"
// LANGUAGE_VERSION: 1.2
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

class Foo<T>(vararg val p: T)

fun test() {
    Foo(p = 123<caret>)
}
// IGNORE_K2

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithArrayOfWithSpreadOperatorInFunctionFix