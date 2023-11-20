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
// https://youtrack.jetbrains.com/issue/KT-63627
// IGNORE_K1
// IGNORE_K2