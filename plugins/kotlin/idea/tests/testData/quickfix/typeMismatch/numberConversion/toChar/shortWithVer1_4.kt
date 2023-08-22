// "Convert expression to 'Char'" "true"
// API_VERSION: 1.4
fun char(x: Char) {}

fun test(s: Short) {
    char(<caret>s)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix