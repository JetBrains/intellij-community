// "Change type of 'f' to '() -> Unit'" "true"
fun foo() {
    val f: () -> Int =<caret> {
        var x = 1
        x += 21
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionLiteralReturnTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix