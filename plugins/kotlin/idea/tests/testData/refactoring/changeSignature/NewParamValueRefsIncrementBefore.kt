fun f<caret>oo(a: Int, b: Int): Int {
    return 42
}

fun test() {
    var x = 1
    foo(++x, x++)
}