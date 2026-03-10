// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: Reference has a nullable type '((Int) -> Unit)?'. Use explicit '?.invoke' to make a function-like call instead.

interface Foo {
    val bar: ((Int) -> Unit)?
}

fun Foo.test() {
    <caret>bar(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction