// "Surround with lambda" "false"
// ERROR: Type mismatch: inferred type is String but (Int) -> String was expected
fun simple() {
    str(<caret>"foo")
}

fun str(block: (num: Int) -> String) {}