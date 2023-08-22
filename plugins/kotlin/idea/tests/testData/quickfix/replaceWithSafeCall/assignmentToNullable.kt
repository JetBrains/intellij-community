// "Replace with safe (?.) call" "true"
// WITH_STDLIB
var i: Int? = 0

fun foo(s: String?) {
    i = s<caret>.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix