// "Replace with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_CALL
class T {
    fun foo(s: String?) = s<caret>.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallFix