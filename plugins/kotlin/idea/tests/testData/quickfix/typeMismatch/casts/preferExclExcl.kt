// "Cast expression 's' to 'String'" "false"
// ACTION: Add 's =' to argument
// ACTION: Add 'toString()' call
// ACTION: Add non-null asserted (s!!) call
// ACTION: Change parameter 's' type of function 'bar' to 'String?'
// ACTION: Create function 'bar'
// ACTION: Surround with null check
// ACTION: Wrap with '?.let { ... }' call
// ERROR: Type mismatch: inferred type is String? but String was expected
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'String?', but 'String' was expected.

fun foo(s: String?) {
    bar(<caret>s)
}

fun bar(s: String){}