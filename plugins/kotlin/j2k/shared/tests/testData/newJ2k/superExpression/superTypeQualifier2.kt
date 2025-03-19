internal interface A {
    fun foo() {}
}

internal interface B : A {
    override fun foo() {
        super.foo()
    }
}
