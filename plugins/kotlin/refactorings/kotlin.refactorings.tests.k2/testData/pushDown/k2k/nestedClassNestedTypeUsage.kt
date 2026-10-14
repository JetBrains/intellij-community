open class <caret>Foo {
    // INFO: {"checked": "true"}
    class Nested {
        class Inner
    }
}

class Bar : Foo()

fun use(i: Foo.Nested.Inner) = i
