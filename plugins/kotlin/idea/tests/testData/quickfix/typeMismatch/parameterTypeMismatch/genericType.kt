// "Change parameter 'x' type of function 'foo' to 'Any?'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
fun <X: Any> foo(x: X) {}

fun <T> bar(x: T) {
    foo(<caret>x)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// IGNORE_K2
// Task for K2: KTIJ-33274