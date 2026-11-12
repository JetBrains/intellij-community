// SAFE_DELETE_OPTIONS: deleteHierarchy=false
open class A {
    open fun foo(value: Int) {}
}

class B : A() {
    override fun foo(<caret>value: Int) {}
}
