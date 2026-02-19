// NEW_NAME: m
// RENAME: member
class A {
    private fun m() {}
    private inner class B {

        private fun b() {
            m()
        }
        private fun <caret>a() {}
    }
}