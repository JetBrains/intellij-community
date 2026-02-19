fun fibu(<caret>unused: Int, n: Int): Int =
    if (n <= 2) 1 else fibu(unused, n - 2) + fibu(unused, n - 1)

fun test() {
    fibu(0, 5)
}
// IGNORE_K1