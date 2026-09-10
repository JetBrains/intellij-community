interface Foo {
    operator fun invoke(action: () -> String, other: Int = 10) {}
}

fun test(foo: Foo) {
    foo({ "hello" })
}