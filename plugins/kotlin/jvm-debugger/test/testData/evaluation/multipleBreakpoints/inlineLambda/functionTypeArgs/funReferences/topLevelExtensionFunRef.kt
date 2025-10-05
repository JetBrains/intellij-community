// IGNORE_K1

fun Int.bar() = this * 2

fun main() {
    foo(Int::bar)
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 84: I
    //Breakpoint!
    val x = 1
}