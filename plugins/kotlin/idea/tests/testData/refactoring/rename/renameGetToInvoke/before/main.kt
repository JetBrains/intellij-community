interface Foo {
    operator fun get(action: () -> String) {}
}

fun test(foo: Foo) {
    foo[{ "hello" }]
}