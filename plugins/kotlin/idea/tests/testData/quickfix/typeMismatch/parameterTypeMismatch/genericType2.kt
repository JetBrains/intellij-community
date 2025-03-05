// "Change parameter 'x' type of function 'foo' to 'Any?'" "true"
fun <X: Any, Y> foo(x: X, y: Y) {}

fun <T> bar(x: T) {
    foo(<caret>x, "")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// IGNORE_K2