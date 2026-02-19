// IGNORE_K1

fun bar(x: Int) = x * 2

fun main() {
    foo(::bar)
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 84: I
    //Breakpoint!
    val x = 1
}