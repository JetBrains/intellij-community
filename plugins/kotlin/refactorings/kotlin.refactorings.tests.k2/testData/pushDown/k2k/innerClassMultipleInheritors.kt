fun m(i: Foo.Inner) {}

open class <caret>Foo {
    // INFO: {"checked": "true"}
    inner class Inner
}

class First : Foo()

class Second : Foo()
