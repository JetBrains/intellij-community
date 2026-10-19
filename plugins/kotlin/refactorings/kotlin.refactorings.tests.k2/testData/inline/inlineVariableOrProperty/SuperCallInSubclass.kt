// ERROR: Inlining this call would change the target of a super call.
open class Base {
    open val bar: Int get() = 1
}

open class Middle : Base() {
    val foo: Int get() = super.bar
    override val bar: Int get() = 2
}

class Impl : Middle() {
    fun use() = <caret>foo
}
