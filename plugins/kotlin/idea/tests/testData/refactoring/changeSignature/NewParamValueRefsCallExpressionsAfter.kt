fun foo(a: Int, b: Int, p2: Int): Int {
    return 42
}

fun test() {
    foo(1.plus(2), 3.minus(4), 1.plus(2) * 3.minus(4))
}