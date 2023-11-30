// "Convert expression to 'Char'" "true"
fun char(x: Char) {}

fun test(s: Short) {
    char(<caret>s)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix