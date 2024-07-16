package lambdaBreakpointInFunCalledFromInline

object A {
    inline fun inlineFun() {
        // STEP_INTO: 1
        //Breakpoint!, lambdaOrdinal = 1
        nonInlineFun { foo() }
    }

    fun nonInlineFun(lambda: () -> Unit) = lambda()

    fun foo() = Unit
}

fun main() {
    A.inlineFun()
}
