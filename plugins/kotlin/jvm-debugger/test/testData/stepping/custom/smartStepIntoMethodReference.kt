package smartStepIntoMethodReference

fun Any.foo(f: () -> Unit): Any {
    f()
    return this
}

fun Any.fooI(f: (Int) -> Unit): Any {
    f(1)
    return this
}

fun ordinaryFun() {
    println()
}

fun ordinaryFun(a: Int) {
    println(a)
}

inline fun inlineFun() {
    println()
}

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    test()

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    1.fooI(::ordinaryFun).foo(::ordinaryFun).foo(::inlineFun)

    // STEP_OVER: 1
    //Breakpoint!
    test()

    // SMART_STEP_INTO_BY_INDEX: 4
    // RESUME: 1
    1.fooI(::ordinaryFun).foo(::ordinaryFun).foo(::inlineFun)

    // STEP_OVER: 1
    //Breakpoint!
    test()

    // SMART_STEP_INTO_BY_INDEX: 6
    // RESUME: 1
    1.fooI(::ordinaryFun).foo(::ordinaryFun).foo(::inlineFun)
}

fun test() {

}
