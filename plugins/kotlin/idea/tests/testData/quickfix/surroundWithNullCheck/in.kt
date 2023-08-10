// "Surround with null check" "true"
fun test(a: String, b: List<String>?) {
    a <caret>in b
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix