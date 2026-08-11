// "Surround with lambda" "false"
// ERROR: Type mismatch: inferred type is Object but () -> String was expected
// ACTION: Add full qualifier
// ACTION: Change parameter 'block' type of function 'str' to 'Object'
// ACTION: Create function 'str'
// ACTION: Introduce import alias
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun fn() {
    str(<caret>Object())
}

fun str(block: () -> String) {}