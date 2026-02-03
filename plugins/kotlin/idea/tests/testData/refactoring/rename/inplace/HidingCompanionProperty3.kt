// NEW_NAME: a
// RENAME: member
private class A {
    private val a = ""

    companion object {
        private val <caret>m = ""
    }

    private fun b() {
        println(m)
        println(a)
    }
}
// IGNORE_K1