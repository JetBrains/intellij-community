package smartStepWithInlineClass

@JvmInline
value class InlineClass(val str: String) {
    fun foo(f: () -> Unit) {
        f()
        // STEP_OVER: 1
        // SMART_STEP_INTO_BY_INDEX: 2
        // RESUME: 1
        //Breakpoint!
        stopHere()
        1.bar { stopHere() }
    }

    fun foo() {
        stopHere()
    }

    fun Int.bar(f: () -> Unit) {
        f()
    }

    companion object {
        @JvmStatic
        fun foos(f: () -> Unit) {
            f()
            // STEP_OVER: 1
            // SMART_STEP_INTO_BY_INDEX: 2
            // RESUME: 1
            //Breakpoint!
            stopHere()
            1.bars { stopHere() }
        }

        @JvmStatic
        fun Int.bars(f: () -> Unit) {
            f()
        }
    }
}

fun foo(i: InlineClass, f: (InlineClass) -> Unit) {
    f(i)
}

fun foo(f: () -> Unit) {
    f()
}

fun bar(i: InlineClass) {
    stopHere()
}

fun main() {
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    val i = InlineClass("TEXT")
    i.foo { stopHere() }

    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    stopHere()
    InlineClass.foos { stopHere() }

    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    stopHere()
    foo(i::foo)

    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    stopHere()
    foo(i) { stopHere() }

    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    stopHere()
    foo(i) { stopHere() }

    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    stopHere()
    foo(i, ::bar)
}

fun stopHere() {
}
