package a

infix fun Int.foo(other: Int) { this + other }

private fun <caret>test() {
    0 foo 1
}
