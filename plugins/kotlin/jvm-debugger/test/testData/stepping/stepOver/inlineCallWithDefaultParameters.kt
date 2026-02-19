package inlienCallWithDefaultParameters

inline fun foo(
    i: Int = 1,
    j: Int = 2,
    k: () -> Unit = {
        println()
        println()
        println()
    },
    l: Int = 3
) {
    println()
    println()
    println()
}

inline fun bar(i: Int = 1, j: Int = 2, k: () -> Unit = { println() }) {
    println()
    println()
    println()
}

fun main() {
    // STEP_OVER: 8
    //Breakpoint!
    foo()
    foo(1)
    foo(1, 2)
    foo(1, 2, { println() })
    foo(1, 2, {
        println()
    })
    foo(1, 2, { println() }, 3)
    bar()
    bar(1, 2) {
        println()
    }
}
