// NEW_NAME: m
// RENAME: member
open class S {
    val m = ""
}
class A: S() {

    private inner class B {

        private fun b(aClass: A) {
            print(aClass.m)
        }
        private val <caret>a = ""
    }
}