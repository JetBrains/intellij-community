// NEW_NAME: m
// RENAME: member
private class A {

    private class B {
        private fun b() {
            println(m)
        }
        private val <caret>a = ""
    }

    companion object {
        private val m = ""
    }
}
// IGNORE_K1