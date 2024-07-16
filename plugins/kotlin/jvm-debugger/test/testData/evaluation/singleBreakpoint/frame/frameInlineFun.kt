package frameInlineFun

fun main(args: Array<String>) {
    val element = 1
    A().inlineFun {
        element
    }
}

inline fun foo(block: () -> Unit) {
    block()
}

class A {
    inline fun inlineFun(s: (Int) -> Unit) {
        val element = 1.0
        foo {
            foo {
                //Breakpoint!
                s(1)
            }
        }
    }

    val prop = 1
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES

// EXPRESSION: element
// RESULT: 1.0: D

// EXPRESSION: this.prop
// RESULT: 1: I

