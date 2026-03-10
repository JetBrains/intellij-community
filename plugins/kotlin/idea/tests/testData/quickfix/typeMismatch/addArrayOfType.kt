// "Add arrayOf wrapper" "true"
// K2_ERROR: Argument type mismatch: actual type is 'String', but 'Array<String>' was expected.

annotation class ArrAnn(val value: Array<String>)

@ArrAnn(<caret>"123") class My

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddArrayOfTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddArrayOfTypeFix