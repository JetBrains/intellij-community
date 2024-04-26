package filterInlineFunctionCalls

inline fun foo(i: Int): Int {
    return i
}

inline fun fun1(i: Int, j: Int): Int {
    fun2(i)
    fun3(j)
    return 1
}

inline fun fun2(i: Int): Int {
    fun3(i)
    return 2
}

inline fun fun3(i: Int): Int {
    return 3
}

class A {
    inline fun fun1(i: Int, j: Int) {

    }

    inline fun fun2(i: Int): Int {
        return i
    }

    inline fun fun3(i: Int): Int {
        return i
    }
}

fun filterNestedInlineFunctions() {
    foo(1)
    foo(2)
    foo(3)

    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    foo(foo(foo(foo(1))))
}

fun filterInlineFunctionsInClass() {
    val a = A()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    //Breakpoint!
    a.fun1(a.fun2(1), a.fun3(2))

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 2
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 3
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    foo(fun1(fun2(2), fun3(3)))
}

fun main() {
    filterNestedInlineFunctions()
    filterInlineFunctionsInClass()
}
