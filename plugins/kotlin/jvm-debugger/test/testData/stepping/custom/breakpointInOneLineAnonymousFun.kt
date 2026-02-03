package breakpointInOneLineAnonymousFun

fun main(args: Array<String>) {
    // STEP_INTO: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo(fun() { boo() })

    // RESUME: 1
    // STEP_INTO: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo(fun() = boo())
}

fun foo(l: () -> Unit) = l()
fun boo() = Unit
