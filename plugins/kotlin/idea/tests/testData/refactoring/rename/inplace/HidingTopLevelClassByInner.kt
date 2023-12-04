// NEW_NAME: A
// RENAME: member
package rename

open class A

class C: A() {
    class <caret>D

    val a : A = A()
    val d: D = D()
}
// IGNORE_K1