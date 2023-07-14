// "Convert expression to 'Char'" "true"
fun char(x: Char) {}

fun test() {
    char(<caret>1L)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix