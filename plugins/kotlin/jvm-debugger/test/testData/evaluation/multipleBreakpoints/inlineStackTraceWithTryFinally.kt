package inlineStackTraceWithTryFinally

inline fun <R> analyze(
    action: () -> R,
): R {
    println("Start")
    return try {
        action()
    } finally {
        println("End")
    }
}


private fun foo(a: Int, b: Int) {
    analyze {
        foo() ?: return
        println("$a $b")
        //Breakpoint!
        42
    }
}

fun foo(): Int? = 42

fun main() {
    foo(1, 2)
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: a
// RESULT: 1: I
