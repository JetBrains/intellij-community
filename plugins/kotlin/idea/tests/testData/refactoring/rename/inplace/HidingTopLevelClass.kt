// NEW_NAME: A
// RENAME: member
// SHOULD_FAIL_WITH: Class 'B' will be shadowed by class 'A'
package rename
class A

class C {
    class <caret>B

    fun use() {
        val v0 = A()
        val v1 = B()
    }
}