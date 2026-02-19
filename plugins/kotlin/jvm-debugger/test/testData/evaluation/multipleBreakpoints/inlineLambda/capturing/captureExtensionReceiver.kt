// IGNORE_K1

fun main() {
    10.foo1 { 1 }
}

inline fun Int.foo1(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 1: I
    //Breakpoint!
    100.foo2 { block() + this }
}

inline fun Int.foo2(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 11: I
    //Breakpoint!
    0.foo3 { block() + this }
}

inline fun Int.foo3(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 111: I
    //Breakpoint!
    val x = 1
}