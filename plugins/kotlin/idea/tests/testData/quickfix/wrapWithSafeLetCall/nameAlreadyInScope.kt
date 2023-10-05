// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

val it = ""

fun test(s: String?) {
    val name = ""
    bar(<caret>s)
}

fun bar(name: String) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.WrapWithSafeLetCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1