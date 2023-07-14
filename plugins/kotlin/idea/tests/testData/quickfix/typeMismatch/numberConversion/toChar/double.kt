// "Convert expression to 'Char'" "true"
fun char(x: Char) {}

fun test(d: Double) {
    char(<caret>d)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix