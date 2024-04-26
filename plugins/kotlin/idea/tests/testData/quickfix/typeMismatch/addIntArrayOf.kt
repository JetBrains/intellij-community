// "Add intArrayOf wrapper" "true"

annotation class ArrAnn(val value: IntArray)

@ArrAnn(<caret>42) class My

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddArrayOfTypeFix
/* IGNORE_K2 */