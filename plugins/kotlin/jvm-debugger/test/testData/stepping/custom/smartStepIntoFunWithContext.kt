package smartStepIntoFunWithContext

context(Double)
fun funWithContext(lambda: (Int) -> Unit) = lambda(42)

fun testContext() {
    with(42.0) {
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        funWithContext { println() }

        // SMART_STEP_INTO_BY_INDEX: 2
        // RESUME: 1
        //Breakpoint!, lambdaOrdinal = -1
        funWithContext { println() }
    }
}

@JvmInline
value class X(val x: Int)

context(X)
private fun funWithContext2(x: Int) = Unit
private fun getInt(): Int = 5

fun testContextInline() {
    with(X(1)) {
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        funWithContext2(getInt())
    }
}

fun main() {
    testContext()
    testContextInline()
}
