// "Change type of 'myFunction' to 'KFunction2<Int, Int, Boolean>'" "true"
// WITH_STDLIB
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

fun foo() {
    var myFunction: (Int, Int) -> Int = <caret>::verifyData
}

fun verifyData(a: Int, b: Int) = (a > 10 && b > 10)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
// IGNORE_K2
// For K2, see functionReflectType2.kt – it has another action description