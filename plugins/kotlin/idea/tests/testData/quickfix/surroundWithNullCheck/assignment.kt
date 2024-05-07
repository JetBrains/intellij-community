// "Surround with null check" "true"

fun foo(s: String?) {
    var ss: String = ""
    ss = <caret>s
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix