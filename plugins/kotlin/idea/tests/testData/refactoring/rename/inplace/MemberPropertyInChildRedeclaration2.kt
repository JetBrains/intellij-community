// NEW_NAME: p
// RENAME: member
// SHOULD_FAIL_WITH: Property after rename will clash with existing function 'getP' in class 'B'
open class A {
    val p<caret>1 = ""
}
class B(): A() {
    fun getP() = ""
}