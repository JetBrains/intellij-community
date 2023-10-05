// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
fun foo(i: Int) {}

fun test(a: Int?, b: Int?) {
    foo(<caret>a ?: b)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1