// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_IMPLICIT_INVOKE_CALL

fun foo(exec: (() -> Unit)?) {
    <caret>exec()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction