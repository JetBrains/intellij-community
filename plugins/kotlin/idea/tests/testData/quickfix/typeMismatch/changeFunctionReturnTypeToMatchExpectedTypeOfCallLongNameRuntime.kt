// "Change return type of called function 'bar' to 'HashSet<Int>'" "true"
// K2_ERROR: Return type mismatch: expected 'HashSet<Int>', actual 'Any'.

fun bar(): Any = java.util.LinkedHashSet<Int>()
fun foo(): java.util.HashSet<Int> = bar(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForCalled
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix