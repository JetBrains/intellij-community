// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a.apply {
        <caret>length
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix