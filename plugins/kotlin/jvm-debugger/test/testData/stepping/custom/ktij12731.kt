package test

fun main(args: Array<String>) {
    // SMART_STEP_INTO_BY_INDEX: 2
    //Breakpoint!
    f1 {
        test()
    }
}

inline fun f1(f1Param: () -> Unit) {
    f1Param()
}

fun test() {
    println("Hello")
}

// IGNORE_K2