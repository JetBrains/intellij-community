// NEW_NAME: A
// RENAME: member
// SHOULD_FAIL_WITH: Class 'A' is already declared in class 'MyEnum'
enum class MyEnum {
    A, <caret>B;
}