// IGNORE_K1

fun main() {
    foo {
        var x = 1
        x += it
        x
    }
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(42)
    // RESULT: 43: I
    //Breakpoint!
    val x = 1
}