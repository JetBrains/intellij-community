package filterSingleFunctionCall

fun foo(lambda: () -> Int) = lambda()

fun main() {
    //Breakpoint!
    val x = 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1

    // only lambda, not visible for user, as we do step into in case of one target
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    foo { 3 } + 42

    //Breakpoint!
    val y = 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OUT: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    foo { 3 } + 42
}
