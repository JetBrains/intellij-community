// "Surround with arrayOf(...)" "true"

fun foo(vararg s: String) {}

fun test() {
    foo(s = <caret>"value")
}

/* IGNORE_K2 */
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix