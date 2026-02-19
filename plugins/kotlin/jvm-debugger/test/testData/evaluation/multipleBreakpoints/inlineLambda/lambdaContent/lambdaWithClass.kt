// IGNORE_K1

fun main() {
    foo {
        class X(val x: Int)
        X(it).x
    }
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 42: I
    //Breakpoint!
    val x = 1
}