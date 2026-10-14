package sample

open class <caret>Foo {
    // INFO: {"checked": "true"}
    class Nested
}

class Bar : Foo()

/** See [Foo.Nested]. */
fun use() {}
