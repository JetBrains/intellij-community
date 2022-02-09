package filterProperties

val val1: Int
    get() {
        return 1
    }

val val2: Int
    get() {
        return 2
    }

val isTrue: Boolean
    get() {
        return true
    }

val isFalse: Boolean
    get() {
        return false
    }

class A {
    val val1: Int
        get() {
            return 3
        }

    val val2: Int
        get() {
            return 4
        }

    val isTrue: Boolean
        get() {
            return true
        }

    @JvmName("doStuff")
    val isFalse: Boolean
        get() {
            return false
        }
}

fun foo(i: Int, j: Int): Int {
    return i + j
}

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 2
    //Breakpoint!
    val result = (val1 + val2 + val1 * val2) / (val2 - val1)

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    if ((isTrue && isFalse) || (!isFalse && isTrue)) {
        println()
    }

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    if ((isTrue && isFalse) || (!isFalse && isTrue)) {
        println()
    }

    val a = A()

    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    //Breakpoint!
    if ((isTrue && a.isFalse) || (!isFalse && a.isTrue)) {
        println()
    }

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    if ((isTrue && a.isFalse) || (!isFalse && a.isTrue)) {
        println()
    }

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    if ((isTrue && a.isFalse) || (!isFalse && a.isTrue)) {
        println()
    }

    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 0
    //Breakpoint!
    if ((isTrue && a.isFalse) || (!isFalse && a.isTrue)) {
        println()
    }

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // RESUME: 1
    //Breakpoint!
    foo(val1 + val2, val2 + val1)

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 4
    //Breakpoint!
    foo(val1 + a.val2, val2 + a.val1)

    // SMART_STEP_INTO_BY_INDEX: 3
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 3
    //Breakpoint!
    foo(val1 + a.val2, val2 + a.val1)


    // SMART_STEP_INTO_BY_INDEX: 4
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 2
    //Breakpoint!
    foo(val1 + a.val2, val2 + a.val1)


    // SMART_STEP_INTO_BY_INDEX: 5
    // STEP_OVER: 1
    // SMART_STEP_TARGETS_EXPECTED_NUMBER: 1
    //Breakpoint!
    foo(val1 + a.val2, val2 + a.val1)
}
