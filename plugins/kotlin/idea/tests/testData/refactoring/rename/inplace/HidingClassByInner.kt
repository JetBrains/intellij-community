// NEW_NAME: C
// RENAME: member
open class A {
    class <caret>D

    class B {
        class C
        val d : D = D()
        val c : C = C()
    }
}