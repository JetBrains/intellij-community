internal open class Base {
    protected open fun foo() {
        TODO()
    }
}

internal class Derived : Base() {
    public override fun foo() {
        super.foo()
    }
}
