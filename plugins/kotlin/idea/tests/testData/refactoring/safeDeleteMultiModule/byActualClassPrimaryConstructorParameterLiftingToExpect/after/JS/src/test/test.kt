package test

actual class Foo actual constructor() {
    val x = n + 1
    constructor(s: String): this()
}

fun test() {
    Foo("1")
    Foo(s = "1")
    Foo()
    Foo()
}