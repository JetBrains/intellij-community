package localFun

fun testBasic() {
    fun local() = 32

    local()
}

fun testExtension() {
    fun Int.local() = this + 1

    (2).local()
}

fun main() {
    //Breakpoint!
    testBasic()
    testExtension()
}

// STEP_INTO: 10
