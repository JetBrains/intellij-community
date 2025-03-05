// "Surround with lambda" "false"
// ERROR: Null can not be a value of a non-null type () -> String
// ACTION: Add 'block =' to argument
// ACTION: Change parameter 'block' type of function 'str' to '(() -> String)?'
// ACTION: Do not show hints for current method
// K2_AFTER_ERROR: Null cannot be a value of a non-null type 'Function0<String>'.
fun nullFn() {
    str(<caret>null)
}

fun str(block: () -> String) {}