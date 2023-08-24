// "Change to function invocation" "true"
fun bar(i: Int, j: Int) {}

fun test(){
    "$bar<caret>(1, 2)"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix