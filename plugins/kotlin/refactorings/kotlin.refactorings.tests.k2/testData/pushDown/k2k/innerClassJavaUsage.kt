package sample

open class <caret>Foo {
    // INFO: {"checked": "true"}
    inner class Inner
}

class Bar : Foo()
