fun foo() {
    A()
        .getB()
    <caret>.f2().f3()
        .f1()
}

open class A {
    fun getB() = B()
}

class B : A {
    fun f2() = this
    fun f3() = this
    fun f1() {}
}

// EXISTS: f2(), f3(), f1()
// IGNORE_K2
