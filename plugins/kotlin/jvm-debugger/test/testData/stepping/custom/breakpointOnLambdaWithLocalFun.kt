package breakpointOnLambdaWithLocalFun.kt

fun main() {
    basicTest()
    testWithLocalFun()
}

fun basicTest() {
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    123.let { g() }

    // STEP_INTO: 1
    // RESUME: 1
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    listOf(1, 2).map { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo { g() }
}

fun testWithLocalFun() {
    fun localFun() = 100

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    123.let { g() }

    // STEP_INTO: 1
    // RESUME: 1
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    listOf(1, 2).map { g() }

    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!, lambdaOrdinal = 1
    foo { g() }
}

fun foo(l: () -> Unit) = l()
fun g() = Unit
