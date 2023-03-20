package lambdaBreakpoints

fun foo(f1: () -> Unit, f2: () -> Unit = {}, f3: () -> Unit = {}, f4: () -> Unit = {}) {
    f1()
    f2()
    f3()
    f4()
}

fun f1() = println()
fun f2() = println()
fun f3() = println()
fun f4() = println()

fun main() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo({ f1() })

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo({ f1() }, { f2() })

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 2
    foo({ f1() }, { f2() })

    foo(
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = 1
        { f1() },
        { f2() }
    )

    foo(
        { f1() },
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = 1
        { f2() }
    )

    foo(
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = 1
        { f1() }, { f2() },
        { f3() }, { f4() }
    )

    foo(
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = 2
        { f1() }, { f2() },
        { f3() }, { f4() }
    )

    foo(
        { f1() }, { f2() },
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = 1
        { f3() }, { f4() }
    )

    foo(
        { f1() }, { f2() },
        // STEP_INTO: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = 2
        { f3() }, { f4() }
    )
}
