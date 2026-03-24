// "Convert expression to 'Char'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Float', but 'Char' was expected.
fun char(x: Char) {}

fun test() {
    char(<caret>1.0f)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix