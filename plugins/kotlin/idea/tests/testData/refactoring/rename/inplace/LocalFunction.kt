// NEW_NAME: bar
// RENAME: member
fun f() {
    fun <caret>foo(a: Int) {
    }

    foo(1)
    foo(217)
}