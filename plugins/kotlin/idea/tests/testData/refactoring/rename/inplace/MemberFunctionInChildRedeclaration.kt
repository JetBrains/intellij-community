// NEW_NAME: m
// RENAME: member
// SHOULD_FAIL_WITH: Function after rename will clash with existing function 'm' in class 'B'
interface A {
    fun m<caret>1() {}
}
class B: A {
    fun m() {}
}