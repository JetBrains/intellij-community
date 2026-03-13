// "Surround with null check" "true"
// WITH_STDLIB
// K2_ERROR: Non-nullable value required to call an 'iterator()' method in a for-loop.

fun foo(list: List<String>?) {
    for (element in <caret>list) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundWithNullCheckFixFactory$SurroundWithNullCheckFix