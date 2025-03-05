// "Surround with lambda" "false"
// ERROR: Type mismatch: inferred type is Object but () -> String was expected
// ACTION: Add full qualifier
// ACTION: Change parameter 'block' type of function 'str' to 'Object'
// ACTION: Create function 'str'
// ACTION: Introduce import alias
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'Object', but 'Function0<String>' was expected.
fun fn() {
    str(<caret>Object())
}

fun str(block: () -> String) {}