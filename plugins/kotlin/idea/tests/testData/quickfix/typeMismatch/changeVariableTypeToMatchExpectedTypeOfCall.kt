// "Change type of 'bar' to 'String'" "true"
// K2_ERROR: RETURN_TYPE_MISMATCH
val bar: Any = ""
fun foo(): String = bar<caret>

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix