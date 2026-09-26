package smartStepIntoInsideConstructor

fun getInt() = 42

class Clazz1 {
    val propWithInit = getInt()

    init {
        getInt()
    }

    fun foo() {
        getInt()
    }
}

class Clazz2 {
    val lazy: String by lazy { "hello" }

    init {
        val lazy2: String by lazy { "hello" }
    }

    fun foo() {
        val lazy3: String by lazy { "hello" }
    }
}

fun main() {
    // STEP_INTO: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    Clazz1()

    // STEP_INTO: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    Clazz2()
}
