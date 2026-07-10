// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun foo(i: Int) {}

fun test(a: Int?, b: Int?) {
    foo(<caret>a ?: b)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction