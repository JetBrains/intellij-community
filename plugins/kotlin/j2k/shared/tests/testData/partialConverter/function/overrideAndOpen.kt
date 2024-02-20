internal open class A {
    open fun foo() {
        TODO()
    }
}

internal open class B : A() {
    override fun foo() {
        TODO()
    }
}

internal class C : B() {
    override fun foo() {}
}
