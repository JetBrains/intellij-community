// WITH_DEFAULT_VALUE: false

fun foo(a: Int, b: Int): Int {
    return <selection>a * b</selection> / 2
}

fun test() {
    foo(1 + 2, 3 - 4)
}