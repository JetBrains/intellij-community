// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

interface Foo {
    val bar: ((Int) -> Unit)?
}

fun test(foo: Foo) {
    foo.bar<caret>(1)
}
