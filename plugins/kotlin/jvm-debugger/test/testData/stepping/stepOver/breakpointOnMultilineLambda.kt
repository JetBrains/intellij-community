package breakpointOnMultilineLambda

fun invoke(f: () -> Unit) = f()

fun main() {
    invoke {
        //Breakpoint!
        println()
    }
}
