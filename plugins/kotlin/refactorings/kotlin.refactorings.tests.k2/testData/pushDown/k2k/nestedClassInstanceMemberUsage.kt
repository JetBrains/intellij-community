open class <caret>Foo {
    // INFO: {"checked": "true"}
    class Nested {
        fun m() {}
    }
}

class Bar : Foo()

fun use(n: Foo.Nested) = n.m()
