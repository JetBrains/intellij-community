// IGNORE_K1

fun main() {
    val localVal = 1
    foo1 { localVal }
}

inline fun foo1(block: () -> Int) {
    val localVal = 10
    // EXPRESSION: block()
    // RESULT: 1: I
    //Breakpoint!
    foo2 { block() + localVal }
}

inline fun foo2(block: () -> Int) {
    val localVal = 100
    // EXPRESSION: block()
    // RESULT: 11: I
    //Breakpoint!
    foo3 {block() + localVal}
}

inline fun foo3(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 111: I
    //Breakpoint!
    val x = 1
}

