package smartStepIntoLambdasOnOneLine

fun Any.acceptLambda(f: () -> Unit): Any {
    f()
    return this
}

fun foo1() = 1
fun foo2() = 2
fun foo3() = 3

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val pos1 = 1

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // RESUME: 1
    Any().acceptLambda { foo1() }.acceptLambda { foo2() }.acceptLambda { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val pos2 = 2

    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_INTO: 1
    // RESUME: 1
    Any().acceptLambda { foo1() }.acceptLambda { foo2() }.acceptLambda { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val pos3 = 3

    // SMART_STEP_INTO_BY_INDEX: 7
    // STEP_INTO: 1
    // RESUME: 1
    Any().acceptLambda { foo1() }.acceptLambda { foo2() }.acceptLambda { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val pos4 = 1

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    Any().acceptLambda { foo1() }.acceptLambda { foo2() }.acceptLambda { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val pos5 = 2

    // SMART_STEP_INTO_BY_INDEX: 4
    // RESUME: 1
    Any().acceptLambda { foo1() }.acceptLambda { foo2() }.acceptLambda { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val pos6 = 3

    // SMART_STEP_INTO_BY_INDEX: 6
    // RESUME: 1
    Any().acceptLambda { foo1() }.acceptLambda { foo2() }.acceptLambda { foo3() }
}

// IGNORE_K2