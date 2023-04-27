package filterConstructors

class A1()

class A2 constructor()

class A3 {
    init {
    }
}

class A4(x: Int)

class A5 constructor(x: Int)

class A6(x: Int) {
    constructor(y: Double) : this(y.toInt())
}

fun consume(vararg objects: Any) {}

fun testImplicitPrimaryConstructors() {
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    //Breakpoint!
    consume(A1(), A2(), A3())

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    consume(A1(), A2(), A3())

    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    consume(A1(), A2(), A3())
}

fun testExplicitConstructors() {
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    //Breakpoint!
    consume(A4(1), A5(2), A6(3.14))

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    consume(A4(3), A5(4), A6(2.72))

    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    consume(A4(5), A5(6), A6(1.41))
}

class KotlinDemo {
    init {
        println("init")
    }

    fun a(): KotlinDemo = this
    fun b(): KotlinDemo = this
    fun c(): KotlinDemo = this
}

fun `test IDEA-317953`() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    //Breakpoint!
    KotlinDemo().a().b().c()

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    KotlinDemo().a().b().c()

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    KotlinDemo().a().b().c()
}

fun main() {
    testImplicitPrimaryConstructors()
    testExplicitConstructors()
    `test IDEA-317953`()
}

// IGNORE_K2