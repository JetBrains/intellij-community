package test

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    //Breakpoint!
    foo(bar())
}

fun foo(a: Any) {
    println("foo")
}

inline fun bar(): Any = Any()
// IGNORE_K2