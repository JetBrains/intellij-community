// At the moment step-into functionality may work incorrect when we have multiple statements in a single line
// Yet the compiler should not fail with an internal error

fun main() {
    foo(); bar<Int>();
}

fun foo() {}

inline fun <reified T> bar() {
    //Breakpoint!
    println()
}


// EXPRESSION: T::class
// RESULT: Method threw 'java.lang.UnsupportedOperationException' exception.