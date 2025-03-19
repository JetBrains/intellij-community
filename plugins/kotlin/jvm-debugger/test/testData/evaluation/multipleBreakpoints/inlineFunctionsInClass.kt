package inlineFunctionsInClass

class A {
    inline fun foo(fooParam: Int) {
        val xFoo = 1
        bar(2)
        //Breakpoint!
        println()
    }

    inline fun bar(barParam: Int) {
        val xBar = 2
        baz(3)
        //Breakpoint!
        println()
    }

    inline fun baz(bazParam: Int) {
        val xBaz = 3
        //Breakpoint!
        println()
    }
}

fun main() {
    val a = A()
    a.foo(1)
    a.foo(2)
}

// EXPRESSION: xBaz + bazParam
// RESULT: 6: I
// EXPRESSION: xBar + barParam
// RESULT: 4: I
// EXPRESSION: xFoo + fooParam
// RESULT: 2: I

// EXPRESSION: xBaz + bazParam
// RESULT: 6: I
// EXPRESSION: xBar + barParam
// RESULT: 4: I
// EXPRESSION: xFoo + fooParam
// RESULT: 3: I

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
