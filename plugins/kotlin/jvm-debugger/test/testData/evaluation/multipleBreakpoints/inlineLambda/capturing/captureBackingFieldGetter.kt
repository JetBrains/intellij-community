// IGNORE_K1

fun main() {
    val z = x
}

val x: Int = 1
    get() {
        foo { field + 1 }
        return field
    }

inline fun foo(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 2: I
    //Breakpoint!
    val x = 1
}