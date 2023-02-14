// "Wrap with '?.let { ... }' call" "true"
// WITH_STDLIB

interface Foo {
    val bar: ((Int) -> Unit)?
}

fun Foo.test() {
    <caret>bar(1)
}