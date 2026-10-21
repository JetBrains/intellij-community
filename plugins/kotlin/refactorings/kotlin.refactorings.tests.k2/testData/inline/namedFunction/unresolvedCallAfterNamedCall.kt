fun oldFun(p: Int, q: Int) {
    first(p, 0)
    unknownFun(q)
}

fun first(p: Int, q: Int) = p + q

fun test() {
    <caret>oldFun(q = 2, p = 1)
}
