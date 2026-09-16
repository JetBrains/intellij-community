// "Change type of 'complex' to '(String) -> String'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH

val complex: (Int) -> String
    get() = { s: String -> s<caret> }


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix