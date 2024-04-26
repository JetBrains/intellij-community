// IGNORE_K1
<selection>fun f(n: Int): Int {
    if (n < 0) return 0
    return f(n - 1)
}</selection>

fun g(n: Int): Int {
    if (n < 0) return 0
    return g(n - 1)
}