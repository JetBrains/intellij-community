internal interface A {
    fun foo() {
    }
}

internal interface B {
    fun foo() {
    }
}

internal class C : A, B {
    override fun foo() {
        super<A>.foo()
    }
}
