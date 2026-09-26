package smartStepIntoFunWithDefaultArgs

fun Int.foo(a: Int = 1) {
    val a = 1
}

fun Int.foo(a: String, b: Int = 1) {
    val a = 1
}

fun bar() = 1

fun main(args: Array<String>) {
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    bar().foo(2)

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    bar().foo()

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    bar().foo("1", 2)

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    bar().foo("1")

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    bar().fooInt().consume()
}

fun Int.fooInt(a: Int = 1): Int {
    return 42
}

fun Int.consume() = Unit
