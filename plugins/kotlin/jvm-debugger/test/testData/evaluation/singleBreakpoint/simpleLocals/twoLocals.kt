package twoLocals

class A {
    fun f(x: Int): () -> Int {
        fun l1(): Int {
            return x
        }
        fun l2(): Int {
            //Breakpoint!
            return 4
        }
        return ::l2
    }
}

fun main() {
    A().f(10)()
}

// `x` is not live at the time of the call to l1 at the breakpoint:
// It dies with the return from `f`, before the invocation of `l2`.

// Breaks differently on the old backend: there, it cannot find `l1`
// because it's not in the captures of `l2`.

// EXPRESSION: l1()
// RESULT: Cannot find local variable 'x' with type int

// IGNORE_K2