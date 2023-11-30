// NEW_NAME: m
// RENAME: member
open class S {
    val m = ""
}
class A: S() {

    private inner class B {

        private fun b() {
            print(m)
        }
        private val <caret>a = ""
    }
}