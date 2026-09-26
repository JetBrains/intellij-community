// NEW_NAME: v
// RENAME: member
// SHOULD_FAIL_WITH: Variable 'v' is already declared in function 'm'

fun m(<caret>p: Int) {
    val v = 42
    print(p)
    print(v)
}