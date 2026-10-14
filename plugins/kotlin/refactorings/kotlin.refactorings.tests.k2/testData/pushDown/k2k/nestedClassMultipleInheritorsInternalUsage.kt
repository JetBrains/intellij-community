open class <caret>Foo {
    // INFO: {"checked": "true"}
    class Nested
}

class First : Foo() {
    fun use(): Foo.Nested = Foo.Nested()
}

class Second : Foo()
