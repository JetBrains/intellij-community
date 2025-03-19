package filterSmartStepWithInlineClass

@JvmInline
private value class A(val s: String) {
    fun foo(): String {
        return s
    }
}

fun String.bar() = println(this)

@JvmInline
private value class B(val s: UInt) {
    fun foo(): UInt {
        return s
    }
}

fun UInt.bar() = println(this)

fun main() {
    val a = A("a")
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    a.foo().bar()

    val b = B(123u)
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    b.foo().bar()
}
