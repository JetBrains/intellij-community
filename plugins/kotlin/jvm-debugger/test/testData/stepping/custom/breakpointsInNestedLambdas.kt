package breakpointsInNestedLambdas

fun foo(f: () -> Unit) = f()
inline fun bar(f: () -> Unit) = f()

fun main() {
    //Breakpoint! (lambdaOrdinal = 1)
    foo {
        //Breakpoint! (lambdaOrdinal = 1)
        foo {
            //Breakpoint! (lambdaOrdinal = 1)
            foo {
                println()
            }
        }
    }
    //Breakpoint! (lambdaOrdinal = 1)
    bar {
        //Breakpoint! (lambdaOrdinal = 1)
        bar {
            //Breakpoint! (lambdaOrdinal = 1)
            bar {
                println()
            }
        }
    }
}

// RESUME: 6
