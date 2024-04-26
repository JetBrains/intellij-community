// NEW_NAME: m
// RENAME: member
private class A {
    private val <caret>a = ""

    companion object {
        private val m = ""
    }

    private fun b() {
        println(m)
        println(a)
    }
}
// IGNORE_K1