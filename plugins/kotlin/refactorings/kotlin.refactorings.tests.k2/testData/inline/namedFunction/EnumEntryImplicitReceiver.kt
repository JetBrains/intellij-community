enum class Foo {
    A,
    B {
        fun <caret>foo() {
            A
        }
        fun bar() {
            foo()
        }
    }
}