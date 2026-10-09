package sample

open class <caret>Foo {
    // INFO: {"checked": "true"}
    open class Nested {
        fun m() {}
    }
}

class Bar : Foo()

class Other : Foo.Nested() {
    fun use() = m()
}
