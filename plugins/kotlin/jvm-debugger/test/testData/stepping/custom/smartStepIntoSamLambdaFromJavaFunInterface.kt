// FILE: smartStepIntoSamLambdaFromJavaFunInterface.kt
package smartStepIntoSamLambdaFromJavaFunInterface

import forTests.I;

fun Any.acceptLambda(f: () -> Unit): Any {
    f()
    return this
}

fun Any.acceptI(i: I): Any {
    i.f()
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
    Any().acceptI { foo1() } .acceptLambda { foo2() }.acceptI { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val pos2 = 2

    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_INTO: 1
    // RESUME: 1
    Any().acceptI { foo1() } .acceptLambda { foo2() }.acceptI { foo3() }

    // STEP_OVER: 1
    //Breakpoint!
    val pos3 = 3

    // SMART_STEP_INTO_BY_INDEX: 7
    // STEP_INTO: 1
    // RESUME: 1
    Any().acceptI { foo1() } .acceptLambda { foo2() }.acceptI { foo3() }
}

// FILE: forTests/I.java
package forTests;

@java.lang.FunctionalInterface
public interface I {
    int f();
}

// IGNORE_K2