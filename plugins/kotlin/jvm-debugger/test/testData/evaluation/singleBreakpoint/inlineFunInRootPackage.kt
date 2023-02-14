// FILE: otherFile.kt

inline fun foo() = 1

// FILE: test.kt

import foo

public fun main() {
    // EXPRESSION: foo()
    // RESULT: 1: I
    //Breakpoint!
    println()
}
