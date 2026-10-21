// INLINE_METHOD_THIS: true
open class Base {
    open fun foo(): Base {
        this.bar()
        return this
    }

    open fun bar() { print("base") }
}

class Impl : Base() {
    override fun foo(): Base = super<Base>@Impl.<caret>foo()

    override fun bar() { print("impl") }
}
