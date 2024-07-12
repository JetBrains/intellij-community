package smartStepIntoLocalFun

fun testBasic() {
    fun show(x: Int) = print("show: $x")

    fun get(x: Int): Int {
        return x.also { println("get: $x") }
    }

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    show(get(37))

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // RESUME: 1
    //Breakpoint!
    show(get(37))
}

fun testExtension() {
    fun Int.local() = this + 1

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    (2).local().local()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    (2).local().local()
}

// This test differs in containing function name, but all the rest names are identical
// Local function names add modified with $1 suffixes
fun testBasic(q: Int) {
    fun show(x: Int) = print("show: $x")

    fun get(x: Int): Int {
        return x.also { println("get: $x") }
    }

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    show(get(37))

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // RESUME: 1
    //Breakpoint!
    show(get(37))
}

@JvmName("Changed Name") // has not effect on local fun name
fun testContainingJvmName() {
    fun show(x: Int) = print("show: $x")

    fun get(x: Int): Int {
        return x.also { println("get: $x") }
    }

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    show(get(37))

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // RESUME: 1
    //Breakpoint!
    show(get(37))
}

fun testLocalInLocal() {
    fun intermediate() {
        fun show(x: Int) = print("show: $x")

        fun get(x: Int): Int {
            return x.also { println("get: $x") }
        }

        // SMART_STEP_INTO_BY_INDEX: 2
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        //Breakpoint!
        show(get(37))

        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // RESUME: 1
        //Breakpoint!
        show(get(37))
    }
    intermediate()
}

fun lambdaChain(lambda1: () -> String, lambda2: () -> String) = lambda1() + lambda2()

fun testMethoReference() {
    fun localFun1() = "local fun 1"
    fun localFun2() = "local fun 2"

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    lambdaChain(::localFun1, ::localFun2)

    // SMART_STEP_INTO_BY_INDEX: 3
    // RESUME: 1
    //Breakpoint!
    lambdaChain(::localFun1, ::localFun2)
}

fun main() {
    testBasic()
    testExtension()
    testBasic(42)
    testContainingJvmName()
    testLocalInLocal()
    testMethoReference()
}
