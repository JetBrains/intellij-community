fun foo(a: Int, b: Int, p2: Int): Int {
    return 42
}

fun test() {
    var x = 1
    val a = ++x
    val b = x++
    foo(++x, x++, a * b)
}