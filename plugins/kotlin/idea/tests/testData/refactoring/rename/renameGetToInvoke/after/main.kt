interface Foo {
    operator fun invoke(action: () -> String) {}
}

fun test(foo: Foo) {
    foo { "hello" }
}