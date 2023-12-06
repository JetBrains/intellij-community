// "Replace scope function with safe (?.) call" "true"
// WITH_STDLIB
fun foo(a: String?) {
    val b = a // comment1
            // comment2
            .let {
                it<caret>.length
            }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallForScopeFunctionFix