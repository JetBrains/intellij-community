// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun foo(x: String?) {
    bar(<caret>x)
}

fun bar(s: String) = s.hashCode()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1