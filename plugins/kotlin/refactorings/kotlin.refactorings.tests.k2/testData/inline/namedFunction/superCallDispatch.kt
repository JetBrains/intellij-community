// INLINE_METHOD_THIS: true
open class Base {
    open fun foo() {
        bar()
    }

    open fun bar() { print("base") }
}

class Impl : Base() {
    override fun foo() {
        super.<caret>foo()
    }

    override fun bar() { print("impl") }
}
