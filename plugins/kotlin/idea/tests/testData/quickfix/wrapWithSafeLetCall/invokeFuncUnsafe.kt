// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun foo(exec: (() -> Unit)?) {
    <caret>exec()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix