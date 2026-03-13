// "Change to function invocation" "true"
// ERROR: No value passed for parameter 'i'
// K2_ERROR: Function invocation 'foo(...)' expected.
// K2_ERROR: Property access cannot have explicit type arguments.
// K2_AFTER_ERROR: No value passed for parameter 'i'.
fun <T> foo(i: Int) = 42

fun main() {
    val listFoo = foo<caret><String>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToFunctionInvocationFix