// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

interface Foo {
    val bar: ((Int) -> Unit)?
}

fun Foo.test() {
    <caret>bar(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix