// INLINE_METHOD_THIS: true
open class Base {
    open fun foo() { bar() }
    open fun bar() { print("base") }
}

class Receiver {
    fun bar() { print("receiver") }
}

class Host {
    companion object : Base() {
        fun use() = with(Receiver()) { super.<caret>foo() }
        override fun bar() { print("companion") }
    }
}
