fun testTopLevelMethod() {
    val list = listOf(X(1), X(2))
    // STEP_INTO: 1
    //Breakpoint!
    process(list)
}

private fun process(x: X) {
    fun foo() = Unit
    println(x)
}
internal fun process(xs: List<X>) {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    xs.forEach(::process)
}

fun testClassMethod() {
    val list = listOf(X(1), X(2))
    val a = A()
    // STEP_INTO: 1
    //Breakpoint!
    a.process(list)
}

class A {
    private fun process(x: X) {
        fun foo() = Unit
        println(x)
    }
    internal fun process(xs: List<X>) {
        // SMART_STEP_INTO_BY_INDEX: 1
        // RESUME: 1
        xs.forEach(this::process)
    }
}

fun main() {
    testTopLevelMethod()
    testClassMethod()
}

class X(val x: Int)
