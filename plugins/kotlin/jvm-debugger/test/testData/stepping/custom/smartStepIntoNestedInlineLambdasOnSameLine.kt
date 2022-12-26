package smartStepIntoNestedInlineLambdasOnSameLine

fun foo() = 0
fun foo1() = 1
fun foo2() = 2
fun foo3() = 3
fun foo4() = 4
fun foo5() = 5
fun foo6() = 6
fun foo7() = 7
fun foo8() = 8
fun foo9() = 9

fun main() {
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    val x1 = 1
    foo().let { foo1().let { foo2().let { foo3().let { it } } } }.let { foo4().let { foo5().let { foo6().let { it } } } }.let { foo7().let { foo8().let { foo9().let { it } } } }

    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    val x2 = 1
    foo().let { foo1().let { foo2().let { foo3().let { it } } } }.let { foo4().let { foo5().let { foo6().let { it } } } }.let { foo7().let { foo8().let { foo9().let { it } } } }

    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_INTO: 1
    // RESUME: 1
    //Breakpoint!
    val x3 = 1
    foo().let { foo1().let { foo2().let { foo3().let { it } } } }.let { foo4().let { foo5().let { foo6().let { it } } } }.let { foo7().let { foo8().let { foo9().let { it } } } }
}

// IGNORE_K2
