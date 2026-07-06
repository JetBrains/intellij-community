// "Change parameter 'x' type of function 'bar' to 'List<T & Any>'" "false"
// ERROR: Type mismatch: inferred type is List<T> but List<T & Any> was expected
// ACTION: Add 'Any' as upper bound for T to make it non-nullable
// ACTION: Add 'x =' to argument
// ACTION: Cast expression 'x' to 'List<T & Any>'
// ACTION: Change parameter 'x' type of function 'foo' to 'List<T>'
// ACTION: Create function 'foo'
// LANGUAGE_VERSION: 1.8
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE
package a

fun <T> foo(x: List<T & Any>) {}

fun <T> bar(x: List<T>) {
    foo(x<caret>)
}
