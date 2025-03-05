package smartStepIntoInlineInvokeFun

class Clazz0() {
    inline fun inlineFun(block: () -> Unit) {
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        block()
    }
}

fun testInlineFun() {
    Clazz0().inlineFun {
        println("hello")
    }
}

class Clazz1() {
    internal inline fun internalInlineFun(block: () -> Unit) {
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        //Breakpoint!
        block()
    }
}

fun testInternalFun() {
    Clazz1().internalInlineFun {
        println("hello")
    }
}

inline fun foo() = 42
class Clazz2() {
    inline fun inlineFun(block: () -> Int) {
        // SMART_STEP_INTO_BY_INDEX: 2
        // RESUME: 1
        //Breakpoint!
        foo() + block()
    }
}

fun testSeveralInlineFun() {
    Clazz2().inlineFun {
        println("hello")
        32
    }
}

fun main() {
    testInlineFun()
    testInternalFun()
    testSeveralInlineFun()
}