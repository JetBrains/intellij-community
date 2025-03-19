// "Add 'toString()' call" "true"
// PRIORITY: LOW

fun test(): String {
    return 1 <caret>+ 2 * 3
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix