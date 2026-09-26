// Parameter used in expression with side effects should NOT be safe to delete
fun compute(<caret>x: Int, n: Int): Int =
    if (n <= 1) 1 else compute(x + 1, n - 1)

fun test() {
    compute(0, 5)
}
