package breakpointsInNestedLambdas

inline fun foo(f: () -> Int) = f()
fun bar(f: () -> Int) = f()
fun foo1() = 1

fun main(args: Array<String>) {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = -1)
    bar { foo { foo1() } }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint! (lambdaOrdinal = 2)
    bar { foo { foo1() } }

    // STEP_INTO: 1
    //Breakpoint! (lambdaOrdinal = 1)
    bar { foo { foo1() } }
}
