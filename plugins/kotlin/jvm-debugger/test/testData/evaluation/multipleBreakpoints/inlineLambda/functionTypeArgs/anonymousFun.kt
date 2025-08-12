// IGNORE_K1

fun main() {
    foo(fun(x: Int) = x * 2)
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 84: I
    //Breakpoint!
    val x = 1
}