open class A {
    open fun <caret>f() {}
}
class B : A() {
    override fun f() {}
}