// "Change parameter 'x' type of function 'foo' to 'Any?'" "true"
fun <X: Any, Y> foo(x: X, y: Y) {}

fun <T> bar(x: T) {
    foo(<caret>x, "")
}