// IGNORE_K1

fun main() {
    var localVar = 1
    foo1 {
        localVar *= 2
        localVar
    }
}

inline fun foo1(block: () -> Int) {
    var localVar = 10
    // EXPRESSION: block()
    // RESULT: 2: I
    //Breakpoint!
    foo2 {
        localVar *= 2
        block() + localVar
    }
}

inline fun foo2(block: () -> Int) {
    var localVar = 100
    // EXPRESSION: block()
    // RESULT: 24: I
    //Breakpoint!
    foo3 {
        localVar *= 2
        block() + localVar
    }
}

inline fun foo3(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 248: I
    //Breakpoint!
    val x = 1
}