// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun test(s: String?) {
    nullable(nullable(notNull(notNull(<caret>s))))
}

fun notNull(name: String): String = name
fun nullable(name: String?): String = ""
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.WrapWithSafeLetCallFixFactories$WrapWithSafeLetCallModCommandAction