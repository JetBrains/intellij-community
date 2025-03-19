// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
// K2_AFTER_ERROR: Missing return statement.

fun test(s: String?): String? {
    if (true) {
        notNull(notNull(<caret>s))
    }
}

fun notNull(name: String): String = name
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction