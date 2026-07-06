// "Surround with arrayOf(...)" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_FUNCTION_ERROR

package foo.bar

class A

fun foo(vararg a: A) {}

fun test() {
    foo(a = <caret>foo.bar.A())
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$SurroundWithArrayModCommandAction