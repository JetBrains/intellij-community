// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

operator fun Int.invoke() = this

fun foo(arg: Int?) {
    <caret>arg()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1