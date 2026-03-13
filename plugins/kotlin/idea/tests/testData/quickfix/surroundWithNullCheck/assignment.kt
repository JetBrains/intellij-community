// "Surround with null check" "true"
// K2_ERROR: Assignment type mismatch: actual type is 'String?', but 'String' was expected.

fun foo(s: String?) {
    var ss: String = ""
    ss = <caret>s
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix