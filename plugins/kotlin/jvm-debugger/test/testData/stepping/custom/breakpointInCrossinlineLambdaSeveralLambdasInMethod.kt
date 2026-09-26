package breakpointInCrossinlineLambdaSeveralLambdasInMethod

fun main() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo({ f() }) { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo({ f() }) { g() }
}

fun f() = Unit
fun g() = Unit

inline fun foo(crossinline body: () -> Unit, crossinline body2: () -> Unit) {
    Runnable { body() }.run()
    Runnable { body2() }.run()
}
