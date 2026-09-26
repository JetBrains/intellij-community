package smartStepIntoConstructorWithValueClassParam

class Clazz(var name: String, var age: UInt) {
    fun foo() = name
}

fun testValueClass() {
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    Clazz("hello", 42u).foo()
}

fun main() {
    testValueClass()
}
