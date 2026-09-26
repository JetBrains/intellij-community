internal open class A {
    open fun a() {
        TODO()
    }
}

internal class B : A() {
    override fun a() {}
}
