// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: Reference has a nullable type '(() -> Unit)?'. Use explicit '?.invoke' to make a function-like call instead.

fun foo(exec: (() -> Unit)?) {
    <caret>exec()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction