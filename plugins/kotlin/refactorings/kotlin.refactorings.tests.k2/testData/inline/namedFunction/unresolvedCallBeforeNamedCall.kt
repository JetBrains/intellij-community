fun oldFun(p: Int, q: Int): Int {
    unknownFun(p)
    return second(0, q)
}

fun second(p: Int, q: Int) = p - q

fun test(): Int {
    return <caret>oldFun(q = 2, p = 1)
}
