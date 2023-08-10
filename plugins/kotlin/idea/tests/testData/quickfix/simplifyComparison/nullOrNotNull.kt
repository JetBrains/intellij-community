// "Simplify comparison" "true"
fun foo(x: Int) {
    if (<caret>x != null || x == null) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SimplifyComparisonFix