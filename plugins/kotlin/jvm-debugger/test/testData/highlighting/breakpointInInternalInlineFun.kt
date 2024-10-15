package breakpointInInternalInlineFun

fun foo() = Unit

class Clazz() {
    internal inline fun internalInlineFun(block: () -> Unit) {
        block()
    }
}

fun testInternalInlineFun() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    Clazz().internalInlineFun { foo() }
}

fun main() {
    testInternalInlineFun()
}
