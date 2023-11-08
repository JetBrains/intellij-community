// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun f(s: String, action: (String.() -> Unit)?) {
    s.action<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1