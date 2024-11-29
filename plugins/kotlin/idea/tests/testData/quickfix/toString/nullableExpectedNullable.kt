// "Add safe '?.toString()' call" "true"
// PRIORITY: LOW
// ACTION: Add 'toString()' call
// ACTION: Add safe '?.toString()' call
// ACTION: Change parameter 'a' type of function 'bar' to 'Any?'
// ACTION: Create function 'bar'

fun foo() {
    bar(null as Any?<caret>)
}

fun bar(a: String?) {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddToStringFix