// "Surround with intArrayOf(...)" "true"

fun foo(vararg s: Int) {}

fun test() {
    foo(s = <caret>1)
}

/* IGNORE_K2 */
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix