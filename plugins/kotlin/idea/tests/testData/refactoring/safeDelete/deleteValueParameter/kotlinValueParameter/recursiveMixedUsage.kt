fun mixed(<caret>x: Int, n: Int): Int {
    println(x)  // actual use
    return if (n <= 1) 1 else mixed(x, n - 1)
}

fun test() {
    mixed(42, 5)
}
// IGNORE_K1