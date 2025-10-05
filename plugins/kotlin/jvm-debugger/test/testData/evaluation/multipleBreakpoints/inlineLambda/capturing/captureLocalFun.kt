// IGNORE_K1

fun main() {
    fun locFun() = 1
    foo1 { locFun() }
}

inline fun foo1(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 1: I
    //Breakpoint!
    val x = 1
}