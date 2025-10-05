// IGNORE_K1

fun main() {
    foo {
        object { val x = it }.x
    }
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 42: I
    //Breakpoint!
    val x = 1
}