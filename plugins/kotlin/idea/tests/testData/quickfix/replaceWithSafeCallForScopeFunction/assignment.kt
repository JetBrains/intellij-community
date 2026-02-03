// "Replace scope function with safe (?.) call" "true"
// WITH_STDLIB
var i = 0

fun foo(a: String?) {
    i = a.run {
        length<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithSafeCallForScopeFunctionFix