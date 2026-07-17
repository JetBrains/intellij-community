// "Replace scope function with safe (?.) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_CALL
fun foo(a: String?) {
    a.let {
        it<caret>.length
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallForScopeFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallForScopeFunctionFix