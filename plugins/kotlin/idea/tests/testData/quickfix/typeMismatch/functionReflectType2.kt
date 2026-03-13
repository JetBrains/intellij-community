// "Change type of 'myFunction' to '(Int, Int) -> Boolean'" "true"
// WITH_STDLIB
// K2_ERROR: Initializer type mismatch: expected '(Int, Int) -> Int', actual 'KFunction2<Int, Int, Boolean>'.

fun foo() {
    var myFunction: (Int, Int) -> Int = <caret>::verifyData
}

fun verifyData(a: Int, b: Int) = (a > 10 && b > 10)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix