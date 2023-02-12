package lambdaWithImplicitReturn

fun main(args: Array<String>) {
    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        println("1")
    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        println("1")
        return@lambda
    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        println("1")
        return@lambda

        // some comments

    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        println("1")
        var a = 1; if (a + 1 == 1) { // some condition which can not be optimized by compiler
            return@lambda // early-return
        }
        println("1.2")
    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        println("1")
        Unit
    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        "1".toInt()
    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        unitMethod()
    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".lambda {
    }.lambda { println("2") }
    // SMART_STEP_INTO_BY_INDEX: 4
    // RESUME: 1

    //Breakpoint! (lambdaOrdinal = -1)
    "1".intLambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        42
    }.lambda { println("2") }

    //Breakpoint! (lambdaOrdinal = -1)
    "1".samIntLambda {
        // SMART_STEP_INTO_BY_INDEX: 4
        // RESUME: 1
        2
    }.lambda { println("2") }
}

fun interface SamLambda {
    fun run(): Int
}

fun unitMethod() = Unit

fun <T> T.lambda(l: () -> Unit) {
    l.invoke()
}

fun <T> T.intLambda(l: () -> Int) {
    l.invoke()
}

fun <T> T.samIntLambda(l: SamLambda) {
    l.run()
}
// IGNORE_K2
