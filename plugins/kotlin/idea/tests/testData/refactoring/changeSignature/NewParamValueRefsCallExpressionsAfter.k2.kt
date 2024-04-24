fun foo(a: Int, b: Int, p2: Int): Int {
    return 42
}

fun test() {
    val a = 1.plus(2)
    val b = 3.minus(4)
    foo(1.plus(2), 3.minus(4), a * b)
}
