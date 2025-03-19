package lambdas

fun main() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo({ boo() })


    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo2({ boo() }) { boo2() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo2({ boo() }) { boo2() }
}

fun foo(l: () -> Unit) = l()
fun foo2(l: () -> Unit, r: () -> Unit) = run { l(); r() }
fun boo() = Unit
fun boo2() = Unit
