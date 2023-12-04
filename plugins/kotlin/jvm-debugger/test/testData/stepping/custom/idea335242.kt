package idea335242

fun main() {
    // STEP_INTO: 1
    //Breakpoint!
    foo(true, true, false)

    //Breakpoint!
    println()
}

inline fun foo(a: Boolean, b: Boolean, c: Boolean) {
    // SMART_STEP_INTO_BY_INDEX: 6
    1.letIf(a) { it }.letIf(b) { it?.plus(1) }.letIf(c) { it?.plus(2) }
    // RESUME: 1
    println()
}

inline fun <T, R> T.letIf(condition: Boolean, block: (T) -> R?): R? {
    return if (condition) block(this) else null
}

// IGNORE_K2
