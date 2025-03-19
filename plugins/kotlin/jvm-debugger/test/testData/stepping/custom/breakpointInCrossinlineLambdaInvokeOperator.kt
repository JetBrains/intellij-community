package breakpointInCrossinlineLambdaInvokeOperator

class Foo {
    inline operator fun invoke(crossinline body: () -> Unit) {
        val runnable = object : Runnable {
            override fun run() = body()
        }
        runnable.run()
    }
}

fun main() {
    val foo = Foo()

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo { f() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo { f() }; foo { g() }
}

fun f() = Unit
fun g() = Unit
