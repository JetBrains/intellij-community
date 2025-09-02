// BIND_TO B.Companion
// BIND_RESULT B
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