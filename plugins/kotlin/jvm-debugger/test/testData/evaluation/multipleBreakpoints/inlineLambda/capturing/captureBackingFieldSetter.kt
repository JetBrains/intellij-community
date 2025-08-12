// IGNORE_K1

fun main() {
    x = 3
}

var x: Int = 1
    set(value) {
        foo { field + 1 }
    }

inline fun foo(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 2: I
    //Breakpoint!
    val x = 1
}