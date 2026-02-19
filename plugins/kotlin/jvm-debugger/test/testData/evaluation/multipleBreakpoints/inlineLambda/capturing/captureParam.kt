// IGNORE_K1

fun main() {
    foo(1)
}

fun foo(p: Int) {
    foo1(10) { p }
}

inline fun foo1(p: Int, block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 1: I
    //Breakpoint!
    foo2(100) { block() + p }
}

inline fun foo2(p: Int, block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 11: I
    //Breakpoint!
    foo3 { block() + p }
}

inline fun foo3(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 111: I
    //Breakpoint!
    val x = 1
}