package filterFunctionCallsInTryCatchBlock

fun foo(i: Int, j: Int): Int {
    return i + j
}

fun bar(i: Int): Int {
    return i
}

fun baz(i: Int): Int {
    return i
}

fun inlineFoo(i: Int, j: Int): Int {
    return i + j
}

fun inlineBar(i: Int): Int {
    return i
}

fun inlineBaz(i: Int): Int {
    return i
}

fun testOrdinaryFunctionCallFiltering() {
    try {
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        foo(bar(1), baz(2))
        throw Exception()
    } catch (ex: Exception) {
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        foo(bar(1), baz(2))
    } finally {
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        foo(bar(1), baz(2))
    }
}

fun testInlineFunctionCallFiltering() {
    try {
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        inlineFoo(inlineBar(1), inlineBaz(2))
        throw Exception()
    } catch (ex: Exception) {
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        inlineFoo(inlineBar(1), inlineBaz(2))
    } finally {
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        inlineFoo(inlineBar(1), inlineBaz(2))
    }
}

fun main() {
    testOrdinaryFunctionCallFiltering()
    testInlineFunctionCallFiltering()
}
