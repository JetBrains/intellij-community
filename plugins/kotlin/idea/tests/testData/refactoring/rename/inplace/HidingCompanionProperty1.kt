// NEW_NAME: a
// RENAME: member
private class A {

    private class B {
        private fun b() {
            println(m)
        }
        private val a = ""
    }

    companion object {
        private val <caret>m = ""
    }
}
// IGNORE_K1