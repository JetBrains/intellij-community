// "Replace with array call" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ASSIGNING_SINGLE_ELEMENT_TO_VARARG_IN_NAMED_FORM_ANNOTATION_ERROR

annotation class Some(vararg val strings: String)

@Some(strings = <caret>"value")
class My

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithArrayCallInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithArrayCallInAnnotationFix