// "Wrap with '?.let { ... }' call" "true"
// WITH_RUNTIME
fun foo(i: Int) {}

fun test(a: Int?, b: Int?) {
    foo(<caret>a ?: b)
}