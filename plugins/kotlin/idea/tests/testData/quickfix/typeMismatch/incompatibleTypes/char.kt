// "Convert string to character literal" "true"
fun test(c: Char) {
    when (c) {
        <caret>"." -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix