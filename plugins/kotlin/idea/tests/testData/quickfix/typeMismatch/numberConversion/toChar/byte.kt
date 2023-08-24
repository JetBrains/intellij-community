// "Convert expression to 'Char'" "true"
fun char(x: Char) {}

fun test(b: Byte) {
    char(<caret>b)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix