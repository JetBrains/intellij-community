// "Change type of 'b' to 'Array<Array<Int>>'" "true"
// WITH_STDLIB
val a: Array<Int> = arrayOf(1)
val b: Array<Int> = <caret>arrayOf(a)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix