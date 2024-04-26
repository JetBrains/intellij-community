// FILE: text.kt
package isInsideLocalFun

fun main(args: Array<String>) {
    val a = A()

    fun local() {
        // EXPRESSION: it + 5
        // RESULT: 6: I
        //Breakpoint! (lambdaOrdinal = 1)
        a.foo(1) { 1 }
    }
    local()

    isInsideLocalFunInLibrary.test()
}

class A {
    inline fun foo(i: Int, f: (i: Int) -> Int): A {
        f(i)
        return this
    }
}

// ADDITIONAL_BREAKPOINT: isInsideLocalFunInLibrary.kt / Breakpoint1 / line / 1
// EXPRESSION: it + 10
// RESULT: 15: I


// FILE: isInsideLocalFunInLibrary.kt
package isInsideLocalFunInLibrary

public fun test() {
    val a = A()

    fun local() {
        //Breakpoint1
        a.foo(5) { 1 }
    }
    local()
}

class A {
    inline fun foo(i: Int, f: (i: Int) -> Int): A {
        f(i)
        return this
    }
}
