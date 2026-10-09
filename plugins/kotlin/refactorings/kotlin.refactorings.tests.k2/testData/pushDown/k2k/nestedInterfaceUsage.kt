package sample

open class <caret>Foo {
    // INFO: {"checked": "true"}
    interface Nested
}

class Bar : Foo()

fun use(value: Foo.Nested): Foo.Nested = value
