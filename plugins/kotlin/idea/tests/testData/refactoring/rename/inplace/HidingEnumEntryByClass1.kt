// NEW_NAME: D
// RENAME: member
// SHOULD_FAIL_WITH: Class 'D' is already declared in class 'A'

enum class A {
    <caret>C;
    class D
}