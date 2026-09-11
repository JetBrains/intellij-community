// "Convert string to character literal" "true"
// K2_ERROR: INITIALIZER_TYPE_MISMATCH
fun foo() {
    val c: Char = "."<caret>
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix