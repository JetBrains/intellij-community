// "Add 'toString()' call" "true"
// PRIORITY: LOW
fun test(s: String, i: Int) {
    when (s) {
        <caret>i -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix
/* IGNORE_K2 */