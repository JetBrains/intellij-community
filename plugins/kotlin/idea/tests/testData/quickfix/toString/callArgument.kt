// "Add 'toString()' call" "true"
// PRIORITY: LOW
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo() {
    bar(Any()<caret>)
}

fun bar(a: String) {
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix