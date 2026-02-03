// NEW_NAME: C
// RENAME: member
open class A {
    class <caret>D

    companion object B {
        class C
        val d : D = D()
        val c : C = C()
    }
}
// IGNORE_K1