// NEW_NAME: f
// RENAME: member
package rename
fun f(i: Int) {}
class A {
    fun <caret>b(i: Int) {}

    fun m() {
        f(42)
        b(0)
    }
}

fun test(a: A) {
    f(42)
    a.b(0)
}

