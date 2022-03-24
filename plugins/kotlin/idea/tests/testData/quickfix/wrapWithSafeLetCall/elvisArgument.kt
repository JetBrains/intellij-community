// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB
fun foo(i: Int) {}

fun test(a: Int?, b: Int?) {
    foo(<caret>a ?: b)
}