// NEW_NAME: f
// RENAME: member
// SHOULD_FAIL_WITH: Function 'f' is already declared in package

class <caret>A(i: Int) {
    constructor() : this(42) {}
}
fun f() {}
