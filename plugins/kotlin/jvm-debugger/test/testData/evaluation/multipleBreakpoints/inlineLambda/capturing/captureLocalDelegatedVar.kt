// IGNORE_K1

// IDEA-376094
// IGNORE_K2

import kotlin.properties.Delegates

fun main() {
    var localVar by Delegates.notNull<Int>()
    foo1 {
        localVar = 1
        localVar
    }
}

inline fun foo1(block: () -> Int) {
    var localVar by Delegates.notNull<Int>()
    // EXPRESSION: block()
    // RESULT: 1: I
    //Breakpoint!
    foo2 {
        localVar = 10
        block() + localVar
    }
}

inline fun foo2(block: () -> Int) {
    var localVar by Delegates.notNull<Int>()
    // EXPRESSION: block()
    // RESULT: 11: I
    //Breakpoint!
    foo3 {
        localVar = 100
        block() + localVar
    }
}

inline fun foo3(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 111: I
    //Breakpoint!
    val x = 1
}