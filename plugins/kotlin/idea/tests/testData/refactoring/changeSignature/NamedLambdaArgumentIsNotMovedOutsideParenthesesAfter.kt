fun foo(p11: Int, p2: () -> Unit) {
}

fun bar() {
    foo(1, p2 = { })
}