// BIND_TO B.Companion
class A {
    companion object {
        fun foo() { }
    }
}

class B {
    companion object {
        fun foo() { }
    }
}

fun foo() {
    <caret>A.foo()
}