// "Replace with array call" "true"
// LANGUAGE_VERSION: 1.2

annotation class Some(vararg val strings: String)

@Some(strings = <caret>"value")
class My
/* IGNORE_K2 */

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithArrayCallInAnnotationFix