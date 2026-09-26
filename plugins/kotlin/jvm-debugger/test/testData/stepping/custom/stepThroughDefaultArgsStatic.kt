package stepThroughDefaultArgsStatic

class A {
    fun <T> foo(
        e: T,
        x: Boolean = true
    ): T {
        println(x)
        return e
    }
}

fun test() {
    val a = A()
    // STEP_INTO: 1
    // STEP_OVER: 4
    // RESUME: 1
    //Breakpoint!
    a.foo("")
}

fun main() {
    test()
}
