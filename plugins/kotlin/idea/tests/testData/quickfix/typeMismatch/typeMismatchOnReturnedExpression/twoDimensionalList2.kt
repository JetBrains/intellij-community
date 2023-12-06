// "Change type of 'b' to 'List<List<Int>>'" "true"
// WITH_STDLIB
val a: List<Int> = listOf(1)
val b: List<Int> = <caret>listOf(a)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix