package filterChainedFunctionCallsWithLambdas

typealias FunT = () -> Unit

fun Any.foo(f: FunT): Any {
    return this
}

fun Any.foo(i: Int, j: Int, f: FunT): Any {
    return this
}

fun Any.bar(f: FunT): Any {
    return this
}

fun Any.baz(f: FunT): Any {
    return this
}

inline fun Any.inlineFoo(f: FunT): Any {
    return this
}

inline fun Any.inlineFoo(i: Int, j: Int, f: FunT): Any {
    return this
}

inline fun Any.inlineBar(f: FunT): Any {
    return this
}

inline fun Any.inlineBaz(f: FunT): Any {
    return this
}

fun testNoInlineCalls() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 7
    1.foo { stopHere() }.bar { stopHere() }.foo (1, 2) { stopHere() }.baz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 6
    1.foo { stopHere() }.bar { stopHere() }.foo (1, 2) { stopHere() }.baz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 5
    1.foo { stopHere() }.bar { stopHere() }.foo (1, 2) { stopHere() }.baz { stopHere() }
}

fun testFirstCallIsInline() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 7
    1.inlineFoo { stopHere() }.bar { stopHere() }.foo (1, 2) { stopHere() }.baz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 6
    1.inlineFoo { stopHere() }.bar { stopHere() }.foo (1, 2) { stopHere() }.baz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 5
    1.inlineFoo { stopHere() }.bar { stopHere() }.foo (1, 2) { stopHere() }.baz { stopHere() }
}

fun testFirstAndThirdCallsAreInline() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 7
    1.inlineFoo { stopHere() }.bar { stopHere() }.inlineFoo (1, 2) { stopHere() }.baz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 6
    1.inlineFoo { stopHere() }.bar { stopHere() }.inlineFoo (1, 2) { stopHere() }.baz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 5
    1.inlineFoo { stopHere() }.bar { stopHere() }.inlineFoo (1, 2) { stopHere() }.baz { stopHere() }
}

fun testAllCallsAreInline() {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 7
    1.inlineFoo { stopHere() }.inlineBar { stopHere() }.inlineFoo (1, 2) { stopHere() }.inlineBaz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 6
    1.inlineFoo { stopHere() }.inlineBar { stopHere() }.inlineFoo (1, 2) { stopHere() }.inlineBaz { stopHere() }

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 5
    1.inlineFoo { stopHere() }.inlineBar { stopHere() }.inlineFoo (1, 2) { stopHere() }.inlineBaz { stopHere() }
}

fun main() {
    testNoInlineCalls()
    testFirstCallIsInline()
    testFirstAndThirdCallsAreInline()
    testAllCallsAreInline()
}

fun stopHere() {

}
