// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun test(s: String?) {
    notNull(notNull(<caret>s))
}

fun notNull(name: String): String = name
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.WrapWithSafeLetCallFixFactories$applicator$1