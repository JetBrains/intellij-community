// ERROR: Inlining this call would change the target of a super call.
interface Base {
    val bar: Int get() = 1
}

class Impl : Base {
    val foo: Int get() = super<Base>.bar
}

fun use(value: Impl) = value.<caret>foo
