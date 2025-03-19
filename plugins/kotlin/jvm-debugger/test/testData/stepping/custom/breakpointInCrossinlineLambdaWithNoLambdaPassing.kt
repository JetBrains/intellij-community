package breakpointInCrossinlineLambdaWithNoLambdaPassing

fun f() = Unit
fun g() = Unit

fun main() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo { f() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo { f() }; foo { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo(1) { f() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo(1) { f() }; foo { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo(1) { f() }; foo { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo { f() }; foo(1) { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo { f() }; foo(1) { g() }
}
inline fun foo(crossinline body: () -> Unit) {
    body()
}

inline fun foo(x: Int, crossinline body: () -> Unit) {
    val runnable = object : Runnable {
        override fun run() = body()
    }
    runnable.run()
}
