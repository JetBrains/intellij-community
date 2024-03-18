fun length(): Int = 1

fun X.foo(k: Int): Boolean {
    return length() - k > 0
}

class X(val k: Int) {}

fun test() {
    X(0).foo(2)
}
