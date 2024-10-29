class A

typealias <caret>X = A

fun foo() {
    val x: X = X()
}
// IGNORE_K2