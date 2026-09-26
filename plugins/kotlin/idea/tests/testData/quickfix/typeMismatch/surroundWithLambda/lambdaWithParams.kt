// "Surround with lambda" "false"
// ERROR: Type mismatch: inferred type is String but (Int, Int) -> String was expected
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun simple() {
    str(<caret>"foo")
}

fun str(block: (num1: Int, num2: Int) -> String) {}