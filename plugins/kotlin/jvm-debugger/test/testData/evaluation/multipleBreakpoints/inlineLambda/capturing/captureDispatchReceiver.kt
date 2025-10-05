// IGNORE_K1

fun main() {
    X().bar()
}

class X {

    val prop = 1

    inline fun bar() {
        Y().bar { prop + 10 }
    }
}

class Y {
    val prop = 100

    inline fun bar(block: () -> Int) {
        // EXPRESSION: block()
        // RESULT: 11: I
        //Breakpoint!
        Z().bar { block() + prop + 1000 }
    }
}

class Z {
    val prop = 10000

    inline fun bar(block: () -> Int) {
        // EXPRESSION: block()
        // RESULT: 1111: I
        //Breakpoint!
        foo { block() + prop + 100000 }
    }
}

inline fun foo(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 111111: I
    //Breakpoint!
    val x = 1
}