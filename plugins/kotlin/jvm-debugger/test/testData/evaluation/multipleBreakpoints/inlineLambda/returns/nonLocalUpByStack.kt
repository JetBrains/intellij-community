// IGNORE_K1

fun main() {
    foo1 { return }
}

inline fun foo1(block: () -> Unit) {
    foo2 { block() }
}

inline fun foo2(block: () -> Unit) {
    foo3 { block() }
}

inline fun foo3(block: () -> Unit) {
    // EXPRESSION: block()
    // RESULT: 'return' is prohibited here.
    //Breakpoint!
    val x = 1
}