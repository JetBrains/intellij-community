// "Convert string to character literal" "true"
// K2_ERROR: Argument type mismatch: actual type is 'String', but 'Char' was expected.
fun foo(x: Char) {}

fun bar() {
    foo("."<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix