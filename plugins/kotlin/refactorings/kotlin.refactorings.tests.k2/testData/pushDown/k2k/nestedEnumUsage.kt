package sample

open class <caret>Foo {
    // INFO: {"checked": "true"}
    enum class Nested { VALUE }
}

class Bar : Foo()

fun use(): Foo.Nested = Foo.Nested.VALUE
