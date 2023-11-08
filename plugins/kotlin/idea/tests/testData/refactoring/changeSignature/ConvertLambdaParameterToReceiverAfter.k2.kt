fun (() -> Int).foo(x: Int, y: Int): Int {
    return x + this@foo()
}

fun bar() {
    {
        3
    }.foo(1, 2)
}