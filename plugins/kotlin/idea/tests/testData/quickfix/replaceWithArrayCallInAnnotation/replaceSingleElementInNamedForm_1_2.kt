// "Replace with array call" "true"
// LANGUAGE_VERSION: 1.2
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

annotation class Some(vararg val strings: String)

@Some(strings = <caret>"value")
class My

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithArrayCallInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithArrayCallInAnnotationFix