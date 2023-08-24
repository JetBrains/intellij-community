// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
var i = 0

fun foo(a: String?) {
    a.run {
        i = <caret>length ?: 0
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix