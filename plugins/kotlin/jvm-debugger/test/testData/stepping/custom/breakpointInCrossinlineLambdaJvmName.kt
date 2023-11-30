package breakpointInCrossinlineLambdaJvmName

fun main() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo { f() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo { f() }; foo { g() }
}

@JvmName("boo")
inline fun foo(crossinline body: () -> Unit) {
    val runnable = object : Runnable {
        override fun run() = body()
    }
    runnable.run()
}

fun f() = Unit
fun g() = Unit
