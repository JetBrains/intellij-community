// IGNORE_K1

fun main() {
    foo { 42 }
}

inline fun foo(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 42: I
    //Breakpoint!
    val x = 1
}