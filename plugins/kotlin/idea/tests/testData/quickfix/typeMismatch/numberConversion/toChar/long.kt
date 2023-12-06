// "Convert expression to 'Char'" "true"
fun char(x: Char) {}

fun test(l: Long) {
    char(<caret>l)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix