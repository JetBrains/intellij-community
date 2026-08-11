// "Change type of 'myFunction' to '(Int, (Int) -> Boolean) -> Boolean'" "true"
// WITH_STDLIB
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

fun foo() {
    var myFunction: (Int, Int) -> Int = <caret>::verifyData
}

fun verifyData(a: Int, b: (Int) -> Boolean) = b(a)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix