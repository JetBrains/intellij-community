package lineBreakpointWithAnonymousFunction

fun main(args: Array<String>) {
    var x = 42
    x = 4
    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    foo(fun() { println("hello $x") })

    // RESUME: 2
    //Breakpoint!
    foo(fun() { println("hello $x") })

    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    foo { println("hello $x") }

    // RESUME: 1
    //Breakpoint!
    println()

    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    inlineFoo(fun() { println("hello $x") })

    // RESUME: 2
    //Breakpoint!
    inlineFoo(fun() { println("hello $x") })

    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    inlineFoo { println("hello $x") }

    // RESUME: 1
    //Breakpoint!
    println()
}

fun foo(l: () -> Unit) = l()
inline fun inlineFoo(l: () -> Unit) = l()

