package smartStepIntoMethodWithJvmNameAnnotation


@JvmName("foo")
fun renamedFoo(lambda: () -> Int): Int = lambda()

val x: Int
    @JvmName("g")
    get() = 42


fun consume(x: Int) = Unit

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val a = 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
    renamedFoo { 42 }

    // STEP_OVER: 1
    //Breakpoint!
    val b = 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OUT: 1
    // RESUME: 1
    renamedFoo { 42 }

    // SMART_STEP_INTO_BY_INDEX: 2
    //Breakpoint!
    consume(x)
}