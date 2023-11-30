// "Surround with arrayOf(...)" "true"

package foo.bar

class A

fun foo(vararg a: A) {}

fun test() {
    foo(a = <caret>foo.bar.A())
}

/* IGNORE_K2 */
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorBasedQuickFix