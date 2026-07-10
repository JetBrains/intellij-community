// "Convert string to character literal" "true"
// K2_ERROR: INCOMPATIBLE_TYPES
fun test(c: Char) {
    when (c) {
        <caret>"." -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix