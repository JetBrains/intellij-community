// "Add doubleArrayOf wrapper" "true"

annotation class ArrAnn(val name: DoubleArray)

@ArrAnn(name = <caret>3.14) class My

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddArrayOfTypeFix
/* IGNORE_K2 */