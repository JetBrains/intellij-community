open class Base {
    open val bar: Int get() = 1
}

class Impl : Base() {
    val foo: Int get() = super.bar

    fun use() = <caret>foo

    fun useThis() = this.foo

    fun useLabeledThis() = this@Impl.foo
}
