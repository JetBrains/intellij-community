// IGNORE_K1

inline fun foo(block: (Int) -> Int = { it + 1 }) {
    // EXPRESSION: block(42)
    // RESULT: 43: I
    //Breakpoint!
    val x = 1
}

fun main() {
    foo()
}