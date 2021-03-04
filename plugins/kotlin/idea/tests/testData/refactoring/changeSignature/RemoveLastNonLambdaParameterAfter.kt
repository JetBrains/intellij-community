fun foo(cl: () -> Int): Int {
    return 42
}

fun bar() {
    foo {
        2
    }
}