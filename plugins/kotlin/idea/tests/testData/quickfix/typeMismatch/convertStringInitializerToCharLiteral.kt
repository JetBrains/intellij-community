// "Convert string to character literal" "true"
// K2_ERROR: Initializer type mismatch: expected 'Char', actual 'String'.
fun foo() {
    val c: Char = "."<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix