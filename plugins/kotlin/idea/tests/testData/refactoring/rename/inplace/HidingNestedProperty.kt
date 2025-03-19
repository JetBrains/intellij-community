// NEW_NAME: m
// RENAME: member
private class A {
    private val <caret>a = ""
    private inner class B {

        private fun b() {
            println(a)
        }
        private val m = ""
    }
}