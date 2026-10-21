// ERROR: Inlining this call would change the target of a super call.
open class Base {
    open fun bar() = 1
}

class Impl : Base() {
    fun foo() = super.bar()

    fun use(other: Impl) = other.<caret>foo()
}
