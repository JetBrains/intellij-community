package smartStepIntoInlineLambda

inline fun foo(a: Boolean, b: Boolean) {
    // STEP_OVER: 1
    //Breakpoint!
    val x = 1
    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_INTO: 1
    1.letIf(a) { it }.letIf(b) { producer() }
}

fun producer() = 42

inline fun <T, R> T.letIf(condition: Boolean, block: (T) -> R?): R? {
    return if (condition) block(this) else null
}

fun main() {
    foo(true, true)
}
