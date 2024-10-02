package smartStepIntoVarargFun

fun foo() = 42

fun testFun(vararg xs: Int) {
    val x = 1
}

fun testVarargFun() {
    // SMART_STEP_INTO_BY_INDEX: 0
    // RESUME: 1
    //Breakpoint!
    testFun(1, foo())
}

fun testVarargLocalFun() {
    fun localTestFun(vararg xs: Int) {
        val x = 1
    }
    // SMART_STEP_INTO_BY_INDEX: 0
    // RESUME: 1
    //Breakpoint!
    localTestFun(1, foo())
}

fun main() {
    testVarargFun()
    testVarargLocalFun()
}
