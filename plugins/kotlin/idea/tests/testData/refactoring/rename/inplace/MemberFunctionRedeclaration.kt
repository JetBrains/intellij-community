// NEW_NAME: m
// RENAME: member
// SHOULD_FAIL_WITH: Function 'm' is already declared in interface 'A'
interface A {
    fun m() {}
}
class B: A {
    fun m<caret>1() {}
}