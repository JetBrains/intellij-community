package breakpointOnWhen

fun Int.foo() =
    // STEP_OVER: 3
    //Breakpoint!
    when (this) {
        is Number -> 1
        else -> 0
    }

fun main() {
    1.foo()
}
