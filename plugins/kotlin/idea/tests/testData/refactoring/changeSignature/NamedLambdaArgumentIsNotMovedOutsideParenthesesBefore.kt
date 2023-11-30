fun <caret>foo(p1: Int, p2: () -> Unit) {
}

fun bar() {
    foo(1, p2 = { })
}