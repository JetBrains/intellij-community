// INLINE_METHOD_THIS: true
open class Base {
    open fun foo() { bar() }
    open fun bar() { print("base") }
}

fun make(): Base = object : Base() {
    fun use() { super.<caret>foo() }
    override fun bar() { print("anonymous") }
}
