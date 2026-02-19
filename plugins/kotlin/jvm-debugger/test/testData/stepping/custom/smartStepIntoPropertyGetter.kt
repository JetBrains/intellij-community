package smartStepIntoMethodReference

class Service1 {
    fun foo() = println()
    val instance get() = Service1()
}

fun testClassSingleLineProperty() {
    val service = Service1()
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    service.instance.foo()
}

class Service2 {
    fun foo() = println()
    val instance
        get() = Service2()
}

fun testClassMultiLineProperty() {
    val service = Service2()
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    service.instance.foo()
}

class Service3 {
    fun foo() = println()
    companion object {
        val instance get() = Service3()
    }
}

fun testObjectSingleLineProperty() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    Service3.instance.foo()
}

class Service4 {
    fun foo() = println()
    companion object {
        val instance
            get() = Service4()
    }
}

fun testObjectMultiLineProperty() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    Service4.instance.foo()
}

class Service5 {
    fun foo() = println()
    companion object {
        var instance: Service5 set(value) { println() } get() { return Service5() }
    }
}

fun testPropertyWithSetterAndGetterOnOneLine() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    //Breakpoint!
    Service5.instance.foo()
}

fun main() {
    testClassSingleLineProperty()
    testClassMultiLineProperty()
    testObjectSingleLineProperty()
    testObjectMultiLineProperty()
    testPropertyWithSetterAndGetterOnOneLine()
}
