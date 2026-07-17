// "Wrap element with 'arrayOf' call" "false"
// ERROR: Type mismatch: inferred type is String but Array<String> was expected
// ACTION: Add arrayOf wrapper
// ACTION: Change parameter 'value' type of primary constructor of class 'Foo' to 'String'
// ACTION: Convert to raw string literal
// ACTION: Create test
// ACTION: Extract 'Bar' from current file
// ACTION: Make internal
// ACTION: Make private
// ACTION: Wrap with []
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

annotation class Foo(val value: Array<String>)

@Foo(value = "abc"<caret>)
class Bar
