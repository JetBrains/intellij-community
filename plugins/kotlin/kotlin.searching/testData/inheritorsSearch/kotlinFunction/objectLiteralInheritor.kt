open class A {
    open fun <caret>f() {}
}
class B {
    companion object : A {
        override fun f() {}
    }
}