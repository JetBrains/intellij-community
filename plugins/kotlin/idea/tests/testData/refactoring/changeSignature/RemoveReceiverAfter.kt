fun foo(s: String, k: Int): Boolean {
    return s.length - k > 0
}

class X(val k: Int, val l: Int)

fun test() {
    with(X(0, 1)) {
        foo("1", 2)
    }
    foo("1", 2)
}