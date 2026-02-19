// IGNORE_K1

class X {
    fun bar(x: Int) = x * 2
}

fun main() {
    foo(X::bar)
}

inline fun foo(block: (X, Int) -> Int) {
    // EXPRESSION: block(X(), 42)
    // RESULT: 84: I
    //Breakpoint!
    val x = 1
}