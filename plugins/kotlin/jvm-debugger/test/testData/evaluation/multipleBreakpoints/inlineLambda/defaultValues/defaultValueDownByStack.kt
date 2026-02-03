// IGNORE_K1

inline fun foo3(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 43: I
    //Breakpoint!
    val x = 1
}

inline fun foo2(block: (Int) -> Int) {
    foo3(block)
}

inline fun foo1(block: (Int) -> Int = { it + 1 }) {
    foo2(block)
}

fun main() {
    foo1()
}