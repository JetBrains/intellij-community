fun <caret>foo(p1: Int, p2: () -> Unit, p3: () -> Unit) {
}

fun bar() {
    foo(1, { }, { })
}