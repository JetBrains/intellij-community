fun foo(x: Boolean) = Unit

class X

private val CONST_X = X()

fun X.foo() {
    foo(this == CONST_X)
}

fun testEqualsFiltered() {
    val x = X()
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    x.foo()
}

fun testSsiEquals() {
    val x = X()
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    x.foo() // skips when no equals declared
}

class XWithEquals {
    override fun equals(other: Any?): Boolean {
        println()
        return super.equals(other)
    }
}

private val CONST_X2 = XWithEquals()

fun XWithEquals.foo() {
    foo(this == CONST_X2)
}

fun testEqualsFiltered2() {
    val x = XWithEquals()
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    x.foo()
}

fun testSsiEquals2() {
    val x = XWithEquals()
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    x.foo()
}

fun testSsiDirectEquals() {
    val x = X()
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    foo(x.equals(X())) // skips when no equals declared
}

fun testSsiDirectEquals2() {
    val x = XWithEquals()
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    foo(x.equals(XWithEquals()))
}

fun testEqualsNullElminated() {
    val x: X? = X()
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    foo(x == null)
}

fun boo(x1: X?, x2: X?) {
    foo(x1 == x2)
}

fun testEqualsNullNotElminated() {
    val x: X? = X()
    // STEP_INTO: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    boo(x, null)
}

fun main() {
    testEqualsFiltered()
    testSsiEquals()
    testEqualsFiltered2()
    testSsiEquals2()
    testSsiDirectEquals()
    testSsiDirectEquals2()
    testEqualsNullElminated()
    testEqualsNullNotElminated()
}
