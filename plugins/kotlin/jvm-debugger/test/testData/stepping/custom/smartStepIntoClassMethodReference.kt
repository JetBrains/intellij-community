// FILE: smartStepIntoClassMethodReference.kt
package smartStepIntoClassMethodReference

import forTests.B

fun Any.foo(f: () -> Unit): Any {
    f()
    return this
}

fun Any.fooI(f: (Int) -> Unit): Any {
    f(1)
    return this
}

class A {
    fun ordinaryFun() {
        println()
    }

    fun ordinaryFun(a: Int) {
        println(a)
    }

    inline fun inlineFun() {
        println()
    }
}

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val a = A()

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    1.fooI(a::ordinaryFun).foo(a::ordinaryFun).foo(a::inlineFun)

    // STEP_OVER: 1
    //Breakpoint!
    test()

    // SMART_STEP_INTO_BY_INDEX: 4
    // RESUME: 1
    1.fooI(a::ordinaryFun).foo(a::ordinaryFun).foo(a::inlineFun)

    // STEP_OVER: 1
    //Breakpoint!
    test()

    // SMART_STEP_INTO_BY_INDEX: 6
    // RESUME: 1
    1.fooI(a::ordinaryFun).foo(a::ordinaryFun).foo(a::inlineFun)

    // STEP_OVER: 1
    //Breakpoint!
    val b = B()

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    1.foo(b::ordinaryFun)
}

fun test() {

}

// FILE: forTests/B.java
package forTests;

public class B {
    public void ordinaryFun() {
        int a = 1;
    }
}
