fun <caret>foo(x: Int, cl: () -> Int): Int {
    return 42
}

fun bar() {
    foo(1) {
        2
    }
}