package filterFunctionCallsFromInlineClass

@JvmInline
value class A(val str: String) {
    fun foo(): A {
        return this
    }

    private fun foo(i: Int, j: Int): A {
        return this
    }

    fun bar(): A {
        return this
    }

    private fun baz(): A {
        return this
    }

    fun inlineFoo(): A {
        return this
    }

    private fun inlineFoo(i: Int, j: Int): A {
        return this
    }

    fun inlineBar(): A {
        return this
    }

    private fun inlineBaz(): A {
        return this
    }

    fun testNoInlineCalls() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        foo().bar().foo(1, 2).baz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        foo().bar().foo(1, 2).baz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        foo().bar().foo(1, 2).baz()
    }

    fun testFirstCallIsInline() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        inlineFoo().bar().foo(1, 2).baz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        inlineFoo().bar().foo(1, 2).baz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        inlineFoo().bar().foo(1, 2).baz()
    }

    fun testFirstAndThirdCallsAreInline() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        inlineFoo().bar().inlineFoo(1, 2).baz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        inlineFoo().bar().inlineFoo(1, 2).baz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        inlineFoo().bar().inlineFoo(1, 2).baz()
    }

    fun testAllCallsAreInline() {
        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
        inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
        inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()

        // STEP_OVER: 1
        //Breakpoint!
        stopHere()

        // SMART_STEP_INTO_BY_INDEX: 3
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        inlineFoo().inlineBar().inlineFoo(1, 2).inlineBaz()
    }
}

fun Int.foo(a: A): Int {
    return 1
}

fun Int.bar(a: A): Int {
    return 2
}

fun testMethodContainsInlineClassInValueArguments() {
    val a = A("TEXT")

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    1.foo(a).bar(a).foo(a)

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    1.foo(a).bar(a).foo(a)
}

fun main() {
    val a = A("TEXT")
    a.testNoInlineCalls()
    a.testFirstCallIsInline()
    a.testFirstAndThirdCallsAreInline()
    a.testAllCallsAreInline()
    testMethodContainsInlineClassInValueArguments()
}

fun stopHere() {
}
