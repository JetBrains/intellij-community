package internalMethod

fun main() {
    val e = Example()
    e.foo()
}

class Example {
    fun foo() {
        // SMART_STEP_INTO_BY_INDEX: 1
        // STEP_OUT: 1
        // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
        //Breakpoint!
        boo().boo()
    }

    internal fun boo() = this
}
