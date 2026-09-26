open class A {
    open fun <caret>f() {}
}

open class B : A() {
    override fun f() {}
}

class C : A() {
    override fun f() {}
}

class D : B() {
    override fun f() {}
}