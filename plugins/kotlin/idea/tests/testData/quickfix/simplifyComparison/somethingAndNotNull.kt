// "Simplify comparison" "true"
fun foo(x: Int, arg: Boolean) {
    if (arg && <caret>x != null) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SimplifyComparisonFix