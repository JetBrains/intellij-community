// SAFE_DELETE_OPTIONS: deleteHierarchy=false
open class A {
    open fun foo(<caret>unused: Int) {}
}

open class B : A() {
    override fun foo(value: Int) {}
}

class C : B() {
    override fun foo(value: Int) {}
}

interface I {
    fun foo(value: Int)
}

class D : A(), I {
    override fun foo(value: Int) {}
}
