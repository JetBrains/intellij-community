// IGNORE_K1

class X(val x: Int)

fun main() {
    foo(::X)
}

inline fun foo(block: (Int) -> X) {
    // EXPRESSION: block(42).x
    // RESULT: 42: I
    //Breakpoint!
    val x = 1
}