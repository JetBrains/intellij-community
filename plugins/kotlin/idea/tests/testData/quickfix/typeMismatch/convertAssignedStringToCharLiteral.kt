// "Convert string to character literal" "true"
// ERROR: A 'return' expression required in a function with a block body ('{...}')
// K2_AFTER_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
// K2_ERROR: ASSIGNMENT_TYPE_MISMATCH
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
fun bar(): Char {
    val c: Char
    c = "."<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix