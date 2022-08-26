package smartStepIntoLambdasOnManyLines

fun Any.acceptLambda(f: () -> Unit): Any {
    f()
    return this
}

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val pos1 = 1

    // SMART_STEP_INTO_BY_INDEX: 3
    // RESUME: 1
    Any().acceptLambda {
        println(1)
    }.acceptLambda { println(2) }
        .acceptLambda { println(3) }.acceptLambda {
            println(4)
        }
        .acceptLambda {
            println(5)
        }.acceptLambda { println(6) }

    // STEP_OVER: 1
    //Breakpoint!
    val pos2 = 2

    // SMART_STEP_INTO_BY_INDEX: 5
    // RESUME: 1
    Any().acceptLambda {
        println(1)
    }.acceptLambda { println(2) }
        .acceptLambda { println(3) }.acceptLambda {
            println(4)
        }
        .acceptLambda {
            println(5)
        }.acceptLambda { println(6) }

    // STEP_OVER: 1
    //Breakpoint!
    val pos3 = 3

    // SMART_STEP_INTO_BY_INDEX: 7
    // RESUME: 1
    Any().acceptLambda {
        println(1)
    }.acceptLambda { println(2) }
        .acceptLambda { println(3) }.acceptLambda {
            println(4)
        }
        .acceptLambda {
            println(5)
        }.acceptLambda { println(6) }

    // STEP_OVER: 1
    //Breakpoint!
    val pos4 = 4

    // SMART_STEP_INTO_BY_INDEX: 9
    // RESUME: 1
    Any().acceptLambda {
        println(1)
    }.acceptLambda { println(2) }
        .acceptLambda { println(3) }.acceptLambda {
            println(4)
        }
        .acceptLambda {
            println(5)
        }.acceptLambda { println(6) }

    // STEP_OVER: 1
    //Breakpoint!
    val pos5 = 5

    // SMART_STEP_INTO_BY_INDEX: 11
    // RESUME: 1
    Any().acceptLambda {
        println(1)
    }.acceptLambda { println(2) }
        .acceptLambda { println(3) }.acceptLambda {
            println(4)
        }
        .acceptLambda {
            println(5)
        }.acceptLambda { println(6) }

    // STEP_OVER: 1
    //Breakpoint!
    val pos6 = 6

    // SMART_STEP_INTO_BY_INDEX: 13
    // RESUME: 1
    Any().acceptLambda {
        println(1)
    }.acceptLambda { println(2) }
        .acceptLambda { println(3) }.acceptLambda {
            println(4)
        }
        .acceptLambda {
            println(5)
        }.acceptLambda { println(6) }
}

// IGNORE_K2