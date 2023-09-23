// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

fun test(s: String?): String? {
    if (true) {
        notNull(notNull(<caret>s))
    }
}

fun notNull(name: String): String = name

/* IGNORE_K2 */
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix