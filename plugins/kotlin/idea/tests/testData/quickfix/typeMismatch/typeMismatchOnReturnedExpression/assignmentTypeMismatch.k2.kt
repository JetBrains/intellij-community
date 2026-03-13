// "Change type of 'f' to '() -> Unit'" "true"
// K2_ERROR: Return type mismatch: expected 'Int', actual 'Unit'.
fun foo() {
    val f: () -> Int =<caret> {
        var x = 1
        x += 21
    }
}

// IGNORE_K2
// TODO: Drop IGNORE_K2 when KTIJ-36918 is fixed

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionLiteralReturnTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix