// IGNORE_K1

fun main() {
    foo {
        fun local(x : Int) = x * 2
        local(it)
    }
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 84: I
    //Breakpoint!
    val x = 1
}