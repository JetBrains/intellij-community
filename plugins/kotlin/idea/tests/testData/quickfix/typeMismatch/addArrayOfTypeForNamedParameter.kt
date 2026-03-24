// "Add doubleArrayOf wrapper" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Double', but 'DoubleArray' was expected.

annotation class ArrAnn(val name: DoubleArray)

@ArrAnn(name = <caret>3.14) class My

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddArrayOfTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddArrayOfTypeFix