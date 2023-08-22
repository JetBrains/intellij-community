// "Simplify comparison" "true"
fun foo(x: String?) {
    if (x == null) {

    }
    else {
        if (<caret>x == null) {
            bar()
        }
    }
}

fun bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SimplifyComparisonFix