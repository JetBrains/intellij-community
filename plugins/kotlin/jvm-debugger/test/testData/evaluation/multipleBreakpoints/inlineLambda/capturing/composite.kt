// IGNORE_K1

fun main() {
    val localVal = 1
    val localLazyVal by lazy { 2 }
    var localVar = 3

    val test = X(4)

    test.foo1(localVal + 1) {
        localVar *= 5
        localVar + localLazyVal
    }
}

class X(val x: Int) {

    val propLazy by lazy { 6 }

    var propVar: Int = 7
        set(value) {
            foo4 {
                field + 8
            }
            field = value
        }

    inline fun foo1(p: Int, block: () -> Int) {
        val localVal = 9
        foo2 { p + localVal + block() + propLazy + x }
    }

    inline fun foo2(block: () -> Int) {
        var localVar = 10
        val localLazyVal by lazy { 11 }
        foo3 {
            localVar += 12
            block() + localVar + localLazyVal
        }
    }

    inline fun foo3(block: () -> Int) {
        foo4 { block() + propVar }
    }
}

inline fun foo4(block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 78: I
    //Breakpoint!
    val x = 1
}