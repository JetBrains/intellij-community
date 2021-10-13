package filterOrdinaryMethods

fun Any.foo(): Any {
    return this
}

fun Any.foo(i: Int, j: Int): Any {
    return this
}

fun Any.bar(): Any {
    return this
}

fun Any.baz(): Any {
    return this
}

fun Any.inlineFoo(): Any {
    return this
}

fun Any.inlineFoo(i: Int, j: Int): Any {
    return this
}

fun Any.inlineBar(): Any {
    return this
}

fun Any.inlineBaz(): Any {
    return this
}

fun testNoInlineCalls() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    1.foo().bar().foo(1, 2).baz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    1.foo().bar().foo(1, 2).baz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    1.foo().bar().foo(1, 2).baz()
}

fun testFirstCallIsInline() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    1.inlineFoo().bar().foo(1, 2).baz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    1.inlineFoo().bar().foo(1, 2).baz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    1.inlineFoo().bar().foo(1, 2).baz()
}

fun testFirstAndThirdCallsAreInline() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    1.inlineFoo().bar().inlineFoo(1, 2).baz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    1.inlineFoo().bar().inlineFoo(1, 2).baz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    1.inlineFoo().bar().inlineFoo(1, 2).baz()
}

fun testAllCallsAreInline() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    1.inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    1.inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    1.inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()
}

fun main() {
    testNoInlineCalls()
    testFirstCallIsInline()
    testFirstAndThirdCallsAreInline()
    testAllCallsAreInline()
}

fun stopHere() {

}
