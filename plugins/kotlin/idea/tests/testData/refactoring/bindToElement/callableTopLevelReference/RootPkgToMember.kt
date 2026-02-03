// BIND_TO B.C
class A { }

class B {
    class C { }
}

fun foo() {
    val x = ::<caret>A
}