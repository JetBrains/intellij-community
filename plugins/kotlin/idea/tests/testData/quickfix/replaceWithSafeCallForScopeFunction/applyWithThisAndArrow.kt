// "Replace scope function with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    a.apply { ->
        this<caret>.length
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallForScopeFunctionFix