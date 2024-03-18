// NEW_NAME: B
// RENAME: member
package rename

class A

class U {
    class B

    fun use() {
        class Lo<caret>cal

        val v0 = A()
        val v1 = B()
        val v2 = Local()
    }
}
// IGNORE_K1