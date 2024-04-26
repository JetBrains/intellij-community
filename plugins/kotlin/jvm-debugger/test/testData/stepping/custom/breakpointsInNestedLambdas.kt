package breakpointsInNestedLambdas

fun foo(f: () -> Unit) = f()
inline fun bar(f: () -> Unit) = f()

fun main() {
    foo {
        //Breakpoint!
        foo {
            //Breakpoint!
            foo {
                //Breakpoint!
                println()
            }
        }
    }

    bar {
        //Breakpoint!
        bar {
            //Breakpoint!
            bar {
                //Breakpoint!
                println()
            }
        }
    }
}

// RESUME: 6
