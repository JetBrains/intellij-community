// "Change to function invocation" "true"
// ERROR: No value passed for parameter 'i'
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: EXPLICIT_TYPE_ARGUMENTS_IN_PROPERTY_ACCESS
// K2_ERROR: FUNCTION_CALL_EXPECTED
// K2_ERROR: NO_VALUE_FOR_PARAMETER
fun <T> foo(i: Int) = 42

fun main() {
    val listFoo = foo<caret><String>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix