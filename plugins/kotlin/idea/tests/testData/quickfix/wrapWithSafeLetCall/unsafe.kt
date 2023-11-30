// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
fun foo(s: String) {}

fun bar(s: String?) {
    foo(s<caret>.substring(1))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1