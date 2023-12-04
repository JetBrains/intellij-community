// NEW_NAME: p
// RENAME: member
// SHOULD_FAIL_WITH: Parameter 'p' is already declared in function 'm'

fun m(p: Int) {
    val <caret>v = 42
    print(p)
    print(v)
}