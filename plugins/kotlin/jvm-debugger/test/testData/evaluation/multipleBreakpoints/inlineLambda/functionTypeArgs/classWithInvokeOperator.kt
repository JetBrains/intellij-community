// IGNORE_K1

class X: Function1<Int, Int> {
    override operator fun invoke(x: Int) = x * 2
}

fun main() {
    foo(X())
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 84: I
    //Breakpoint!
    val x = 1
}