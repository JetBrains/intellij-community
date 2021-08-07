// "Surround with lambda" "false"
// ERROR: Type mismatch: inferred type is Object but () -> String was expected
// ACTION: Add full qualifier
// ACTION: Change parameter 'block' type of function 'str' to 'Object'
// ACTION: Create function 'str'
// ACTION: Edit method contract of 'Object'
// ACTION: Introduce import alias
fun fn() {
    str(<caret>Object())
}

fun str(block: () -> String) {}