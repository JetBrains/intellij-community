fun oldFun(p: Int, q: Int): Int {
    return first(p, 0) + second(0, q)
}

fun first(p: Int, q: Int) = p + q

fun second(p: Int, q: Int) = p - q

fun test(): Int {
    return <caret>oldFun(q = 2, p = 1)
}
