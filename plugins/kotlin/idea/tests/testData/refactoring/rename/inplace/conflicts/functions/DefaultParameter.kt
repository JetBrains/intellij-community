// NEW_NAME: f
// RENAME: member
// SHOULD_FAIL_WITH: Function 'b' will be shadowed by function 'f'
fun f() {}
fun <caret>b(i: Int = 0) {}

fun test() {
    f()
    b()
}
// IGNORE_K1
