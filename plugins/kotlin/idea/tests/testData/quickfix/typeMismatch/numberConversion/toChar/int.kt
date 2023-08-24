// "Convert expression to 'Char'" "true"
fun char(x: Char) {}

fun test() {
    char(<caret>1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix