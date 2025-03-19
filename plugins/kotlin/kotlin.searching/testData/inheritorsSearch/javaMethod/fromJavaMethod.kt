package p

class B : A {
    override fun foo() = ""
}

class C : A by B() {
    override fun foo() = ""
}

class D : A by B()