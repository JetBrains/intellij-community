// IGNORE_K1

fun main() {
    X(10, 100).foo1 { 1 }
}

class X(val x: Int, val y: Int) {

    inline fun foo1(block: () -> Int) {
        // EXPRESSION: block()
        // RESULT: 1: I
        //Breakpoint!
        foo2 { block() + x }
    }

    inline fun foo2(block: () -> Int) {
        // EXPRESSION: block()
        // RESULT: 11: I
        //Breakpoint!
        foo3 { block() + y }
    }

    inline fun foo3(block: () -> Int) {
        // EXPRESSION: block()
        // RESULT: 111: I
        //Breakpoint!
        val z = 1
    }
}