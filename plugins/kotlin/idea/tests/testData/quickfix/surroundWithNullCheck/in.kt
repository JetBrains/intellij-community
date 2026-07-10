// "Surround with null check" "true"
// K2_ERROR: UNSAFE_OPERATOR_CALL
fun test(a: String, b: List<String>?) {
    a <caret>in b
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix