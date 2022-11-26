// "Convert expression to 'Array' by inserting '.toTypedArray()'" "false"
// ERROR: Type mismatch: inferred type is Array<out String> but Array<String> was expected
// ACTION: Cast expression 'strings' to 'Array<String>'
// ACTION: Change parameter 'strings' type of function 'bar' to 'Array<out String>'
// ACTION: Create function 'bar'

fun foo(vararg strings: String) {
    bar(strings<caret>)
}

fun bar(strings: Array<String>) {
}