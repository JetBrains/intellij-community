// NEW_NAME: C
// RENAME: member
// SHOULD_FAIL_WITH: Class 'C' is already declared in class 'A'

enum class A {
    C;
    class <caret>D
}