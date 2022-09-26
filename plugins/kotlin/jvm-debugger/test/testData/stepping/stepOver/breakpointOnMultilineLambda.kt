package breakpointOnMultilineLambda

fun invoke(f: () -> Unit) = f()

fun main() {
    //Breakpoint! (lambdaOrdinal = 1)
    invoke {
        println()
    }
}
