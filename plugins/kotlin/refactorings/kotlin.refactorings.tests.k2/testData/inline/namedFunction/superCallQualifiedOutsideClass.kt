// ERROR: Inlining this call would change the target of a super call.
interface Base {
    fun bar() = 1
}

class Impl : Base {
    fun foo() = super<Base>.bar()
}

fun use(value: Impl) = value.<caret>foo()
