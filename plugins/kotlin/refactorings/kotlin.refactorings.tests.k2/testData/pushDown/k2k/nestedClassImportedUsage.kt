package sample

import sample.Foo.Nested

open class <caret>Foo {
    // INFO: {"checked": "true"}
    class Nested
}

class Bar : Foo()

fun use(): Nested = Nested()
