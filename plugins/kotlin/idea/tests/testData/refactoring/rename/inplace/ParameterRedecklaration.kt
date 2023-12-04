// NEW_NAME: b
// RENAME: member
// SHOULD_FAIL_WITH: Parameter 'b' is already declared in class 'A'
class A(val <caret>a: String, b: String) {}