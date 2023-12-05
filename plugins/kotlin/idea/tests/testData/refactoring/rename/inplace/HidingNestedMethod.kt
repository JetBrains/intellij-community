// NEW_NAME: m
// RENAME: member
class A {
    private fun <caret>a() {}
    private inner class B {

        private fun b() {
            a()
        }
        private fun m() {}
    }
}