// IGNORE_K1

fun main() {
    foo {
        val lambda = {
            it
        }
        lambda()
    }
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 42: I
    //Breakpoint!
    val x = 1
}