// "Add 'toString()' call" "true"
fun test(s: String, i: Int) {
    when (s) {
        <caret>i -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix