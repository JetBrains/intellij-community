open class Base {
    open fun bar() = 1
}

class Impl : Base() {
    fun foo() = super.bar()

    fun use() = <caret>foo()

    fun useThis() = this.foo()

    fun useLabeledThis() = this@Impl.foo()
}
