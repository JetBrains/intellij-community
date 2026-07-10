// "Let 'B' implement interface 'A<*>'" "false"
// ACTION: Change parameter 'a' type of function 'foo' to 'B'
// ACTION: Create function 'foo'
// ERROR: Type mismatch: inferred type is B but A<*> was expected
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

package let.implement

fun bar() {
    foo(B()<caret>)
}


fun foo(a: A<*>) {
}

interface A<T>
class B