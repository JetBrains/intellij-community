// "Change to function invocation" "true"
// K2_ERROR: FUNCTION_CALL_EXPECTED
// K2_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
fun bar(i: Int, j: Int) {}

fun test(s: String){
    "$bar<caret>(1, 2) sometext $s"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix