// "Surround with intArrayOf(...)" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Int', but 'IntArray' was expected.
// K2_ERROR: Assigning single elements to varargs in named form is prohibited.

fun foo(vararg s: Int) {}

fun test() {
    foo(s = <caret>1)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$SurroundWithArrayModCommandAction