// "Add 'toString()' call" "true"
// PRIORITY: LOW
// K2_ERROR: RETURN_TYPE_MISMATCH

fun test(): String {
    return 1 <caret>+ 2 * 3
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix