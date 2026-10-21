// ERROR: Inlining this call would change the target of a super call.
open class Base {
    open fun bar() = 1
}

open class Middle : Base() {
    fun foo() = super.bar()
    override fun bar() = 2
}

class Impl : Middle() {
    fun use() = <caret>foo()
}
