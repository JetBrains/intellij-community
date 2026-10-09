fun m(i: Foo.Nested) = Foo.Nested()

open class <caret>Foo {
    // INFO: {"checked": "true"}
    class Nested
}

class Bar : Foo()
