// "Replace with safe (this?.) call" "true"
// WITH_STDLIB
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'.
var i = 0

fun foo(a: String?) {
    a.run {
        i = <caret>length
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceImplicitReceiverCallFix