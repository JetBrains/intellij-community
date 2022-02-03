package filterNestedInlineFunctions

inline fun fun1(i: Int, j: Int): Int {
    fun2(i, j)
    fun3(i, j)
    return 1
}

fun fun1(i: Int): Int {
    fun2(i)
    fun3(i)
    return 1
}

inline fun fun2(i: Int, j: Int): Int {
    fun3(i, j)
    return 2
}

fun fun2(i: Int): Int {
    fun3(i)
    return 2
}

fun fun3(i: Int, j: Int): Int {
    fun4(i)
    return 3
}

inline fun fun3(i: Int): Int {
    fun4(i)
    return 3
}

inline fun fun4(i: Int): Int {
    fun5(i)
    return i
}

fun fun5(i: Int): Int {
    fun6(i)
    return i
}

inline fun fun6(i: Int): Int {
    fun7(i)
    return i
}

fun fun7(i: Int): Int {
    fun8(i)
    return i
}

inline fun fun8(i: Int): Int {
    fun9(i)
    return i
}

fun fun9(i: Int): Int {
    return i
}

fun main(args: Array<String>) {
    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 10
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 3
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 9
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 6
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 8
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 7
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 6
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 9
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 5
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 8
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 4
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 11
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 10
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))

    // SMART_STEP_INTO_BY_INDEX: 7
    // STEP_OVER: 2
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    fun1(fun2(fun1(fun2(2)), fun4(fun3(2))), fun3(fun5(fun6(2)), fun7(fun8(2))))
}
