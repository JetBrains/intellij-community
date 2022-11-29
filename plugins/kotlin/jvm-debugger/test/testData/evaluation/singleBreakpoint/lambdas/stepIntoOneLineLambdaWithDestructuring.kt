// ATTACH_LIBRARY: utils
package stepIntoOneLineLambdaWithDestructuring

import destructurableClasses.B

fun foo(f: (B) -> Unit) {
    val b = B()
    // STEP_INTO: 1
    // EXPRESSION: x + y
    // RESULT: 2: I
    //Breakpoint!
    f(b)
}

fun main() {
    foo { (x, _, y, _) -> println() }
}

// PRINT_FRAME
