// "Change return type of enclosing function 'b' to 'Array<Array<Int>>'" "true"
// WITH_STDLIB
val a: Array<Int> = arrayOf(1)
fun b(): Array<Int> {
    return <caret>arrayOf(a)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix