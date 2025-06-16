// "Change type of 'bar' to 'String'" "true"
val bar: Any = ""
fun foo(): String = bar<caret>
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix