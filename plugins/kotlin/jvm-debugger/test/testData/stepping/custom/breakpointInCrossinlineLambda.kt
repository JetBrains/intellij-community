package breakpointInCrossinlineLambda

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
    //Breakpoint!, lambdaOrdinal = 2
    boo { f() }; boo { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo2 { f() }; foo2 { g() }
}

// Test passing crossinline lambda to an inlne function with a different name
inline fun boo(crossinline body: () -> Unit) {
    foo(1, body)
}

// Test passing crossinline lambda to an inlne function with the same name
inline fun foo(crossinline body: () -> Unit) {
    foo(1, body)
}

// Test passing crossinline lambda to an inlne function by using it inside another lambda
inline fun foo2(crossinline body: () -> Unit) {
    foo(1) { f(); body() }
}

inline fun foo(x: Int, crossinline body: () -> Unit) {
    val runnable = object : Runnable {
        override fun run() = body()
    }
    runnable.run()
}

fun f() = Unit
fun g() = Unit
