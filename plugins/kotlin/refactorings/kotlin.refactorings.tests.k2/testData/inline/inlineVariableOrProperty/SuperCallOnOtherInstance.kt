// ERROR: Inlining this call would change the target of a super call.
open class Base {
    open val bar: Int get() = 1
}

class Impl : Base() {
    val foo: Int get() = super.bar

    fun use(other: Impl) = other.<caret>foo
}
