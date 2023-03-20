interface Foo {
    operator fun get(action: () -> String, other: Int = 10) {}
}

fun test(foo: Foo) {
    foo[{ "hello" }]
}