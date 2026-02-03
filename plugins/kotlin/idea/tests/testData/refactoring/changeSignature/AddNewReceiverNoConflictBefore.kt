fun length(): Int = 1

fun <caret>foo(k: Int): Boolean {
    return length() - k > 0
}

class X(val k: Int) {}

fun test() {
    foo(2)
}