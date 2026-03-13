// "Surround with arrayOf(...)" "true"
// K2_ERROR: Argument type mismatch: actual type is 'String', but 'Array<out String>' was expected.
// K2_ERROR: Assigning single elements to varargs in named form is prohibited.

fun foo(vararg s: String) {}

fun test() {
    foo(s = <caret>"value")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$SurroundWithArrayModCommandAction