// NEW_NAME: m
// RENAME: member
class A {
    private val m = ""
    private inner class B {

        private fun b() {
            print(m)
        }
        private val <caret>a = ""
    }
}