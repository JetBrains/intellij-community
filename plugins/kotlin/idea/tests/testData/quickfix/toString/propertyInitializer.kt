// "Add 'toString()' call" "true"
// PRIORITY: LOW
// K2_ERROR: INITIALIZER_TYPE_MISMATCH

val s: String = 1 <caret>+ 2 * 3

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix