// "Change to function invocation" "true"
fun foo() {}

fun test(){
    "$foo<caret>()"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix