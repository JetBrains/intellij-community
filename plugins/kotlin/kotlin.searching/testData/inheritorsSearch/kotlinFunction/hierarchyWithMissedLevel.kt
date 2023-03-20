open class A {
    open fun <caret>f() {}
}
open class B : A() {}
class C : B() {
    override fun f() {}
}