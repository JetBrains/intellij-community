// "Surround with lambda" "false"
// ERROR: Type mismatch: inferred type is String but (Int, Int) -> String was expected
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'String', but 'Function2<@ParameterName(...) Int, @ParameterName(...) Int, String>' was expected.
fun simple() {
    str(<caret>"foo")
}

fun str(block: (num1: Int, num2: Int) -> String) {}