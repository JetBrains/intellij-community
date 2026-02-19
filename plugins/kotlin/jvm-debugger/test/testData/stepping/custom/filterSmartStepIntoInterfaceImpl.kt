package filterSmartStepIntoInterfaceImpl

interface I {
    fun foo(): I {
        return this
    }
}

class IImpl: I

fun main() {
    val i: I = IImpl()
    // STEP_INTO: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    i.foo().foo()
}
