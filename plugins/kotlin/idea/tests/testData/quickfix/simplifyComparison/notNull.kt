// "Simplify comparison" "true"
fun foo(x: Int) {
    if (<caret>x != null) {
        bar()
    }
}

fun bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SimplifyComparisonFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SimplifyComparisonFixFactory$SimplifyComparisonFix