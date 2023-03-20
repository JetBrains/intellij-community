package stepOutFromInlineFunctionThenResume

inline fun foo1(): Int {
    //Breakpoint!
    return 1
}

inline fun foo2(): Int {
    return 2
}

fun main() {
    foo1()
    val x = 1
    //Breakpoint!
    foo2()
}

// STEP_OUT: 1
// RESUME: 2
