// IGNORE_K1

fun main() {
    foo { return }
}

inline fun foo(block: () -> Unit) {
    // EXPRESSION: block()
    // RESULT: 'return' is prohibited here.
    //Breakpoint!
    val x = 1
}