// "Convert expression to 'Char'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
fun char(x: Char) {}

fun test() {
    char(<caret>1L)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix