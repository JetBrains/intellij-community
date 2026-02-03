package breakpointInInlineFunction

object I1 {
    @JvmStatic
    inline fun foo() {
        //Breakpoint!
        println()
    }
}

object T1 {
    fun testBreakpointInInlineFun() {
        //Breakpoint!
        I1.foo()
    }
}

object I2 {
    @JvmStatic
    inline fun foo() {
        I1.foo()
    }
}

object T2 {
    fun testBreakpointInNestedInlineFun() {
        //Breakpoint!
        I2.foo()
    }
}

fun main() {
    T1.testBreakpointInInlineFun()
    T2.testBreakpointInNestedInlineFun()
}
