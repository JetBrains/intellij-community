fun rec(<caret>x: Int, n: Int): Int =
    if (n <= 1) 1 else rec(n = n - 1, x = x)

fun test() {
    rec(0, 5)
}
// IGNORE_K1