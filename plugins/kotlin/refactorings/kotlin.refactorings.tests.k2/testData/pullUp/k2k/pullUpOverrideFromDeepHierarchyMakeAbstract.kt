abstract class Foo {
    abstract fun foo()
}

abstract class Bar : Foo()

abstract class Baz : Bar() {
    // INFO: {"checked": "true", "toAbstract": "true"}
    override fun foo<caret>() {

    }
}
