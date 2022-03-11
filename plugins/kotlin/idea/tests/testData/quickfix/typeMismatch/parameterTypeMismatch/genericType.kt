// "Change parameter 'x' type of function 'foo' to 'Any?'" "true"
fun <X: Any> foo(x: X) {}

fun <T> bar(x: T) {
    foo(<caret>x)
}