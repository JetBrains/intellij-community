// ENABLED_LANGUAGE_FEATURE: GenericInlineClassParameter
package filterFunctionCallsFromInlineClass

@JvmInline
value class D(val v: Int) {
    var x: D
        get() = this
        set(value) = Unit
}

fun consumer(a: D) = Unit
fun producer() = D(1)

fun testPropertyAccess(d: D) {
    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    consumer(d.x)

    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    d.x = producer()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    d.x = producer()
}

fun main() {
    testPropertyAccess(D(1))
}

fun stopHere() {
}

class B {
    class C
}
