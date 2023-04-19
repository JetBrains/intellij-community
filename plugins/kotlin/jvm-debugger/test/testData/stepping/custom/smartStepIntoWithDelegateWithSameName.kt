package smartStepIntoWithDelegateWithSameName

interface A {
    fun foo(): String
}

interface A2 {
    fun foo(i: Int): String
}

class AImpl : A {
    override fun foo() = "impl"
}

class A2Impl : A2 {
    override fun foo(i: Int) = "impl2"
}

class AImplByDelegation(a: A, a2: A2) :
    A by a,
    A2 by a2

fun main(args: Array<String>) {
    val del = AImplByDelegation(AImpl(), A2Impl())
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    del.foo()
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    del.foo(1)
}
// IGNORE_K2
