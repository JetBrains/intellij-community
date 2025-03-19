// "Surround with arrayOf(...)" "true"

fun foo(vararg s: String) {}

fun test() {
    foo(s = <caret>"value")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory$SurroundWithArrayModCommandAction