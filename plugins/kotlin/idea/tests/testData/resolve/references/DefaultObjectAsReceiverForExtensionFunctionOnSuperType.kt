package t

interface Interface

open class A {
    companion object Companion : Interface {

    }
}

fun Interface.foo() {}

fun test() {
    <caret>A.foo()
}


// REF: companion object of (t).A

