// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'String?', but 'String' was expected.

fun test(s: String?) {
    notNull(notNull(<caret>s))
}

fun notNull(name: String): String = name
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction