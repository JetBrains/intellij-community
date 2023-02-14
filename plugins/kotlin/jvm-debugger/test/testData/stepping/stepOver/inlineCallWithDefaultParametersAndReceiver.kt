package inlienCallWithDefaultParametersAndReceiver

inline fun Int.foo(
    i: Int = 1,
    j: Int = 2,
    k: () -> Unit = {
        println()
        println()
        println()
    },
    l: Int = 3
): Int {
    return 1
}

fun main() {
    // STEP_OVER: 15
    //Breakpoint!
    1.foo().foo(1).foo(1, 2).foo(1, 2, { println() }).foo(1, 2, { println() }, 3)
    1.foo()
        .foo(1).foo(1, 2).foo(1, 2, { println() }).foo(1, 2, { println() }, 3)
    1.foo()
        .foo(1)
        .foo(1, 2).foo(1, 2, { println() }).foo(1, 2, { println() }, 3)
    1.foo()
        .foo(1)
        .foo(1, 2).foo(1, 2, { println() })
        .foo(1, 2, { println() }, 3)
    1
        .foo()
        .foo(1)
        .foo(1, 2).foo(1, 2, { println() })
        .foo(1, 2, { println() }, 3)
}
