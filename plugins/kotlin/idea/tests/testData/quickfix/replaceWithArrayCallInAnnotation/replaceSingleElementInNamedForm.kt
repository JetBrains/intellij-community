// "Replace with array call" "true"
// K2_ERROR: Argument type mismatch: actual type is 'String', but 'Array<out String>' was expected.
// K2_ERROR: Assigning single elements to varargs in named form is prohibited.

annotation class Some(vararg val strings: String)

@Some(strings = <caret>"value")
class My

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithArrayCallInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithArrayCallInAnnotationFix