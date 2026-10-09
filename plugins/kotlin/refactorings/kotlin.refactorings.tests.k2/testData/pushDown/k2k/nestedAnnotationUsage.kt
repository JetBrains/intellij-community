package sample

open class <caret>Foo {
    // INFO: {"checked": "true"}
    annotation class Nested
}

class Bar : Foo()

@Foo.Nested
class Usage
