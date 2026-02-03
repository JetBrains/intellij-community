// IGNORE_K1

fun main() {
    fun localFun(x: Int) = x * 2
    foo(::localFun)
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 84: I
    //Breakpoint!
    val x = 1
}