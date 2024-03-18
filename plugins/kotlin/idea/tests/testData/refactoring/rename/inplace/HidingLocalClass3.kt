// NEW_NAME: Local
// RENAME: member
package rename

class <caret>A

class U {
    class B

    fun use() {
        class Local

        val v0 = A()
        val v1 = B()
        val v2 = Local()
    }
}
// IGNORE_K1