package funFromOuterClassInLamdba

fun main(args: Array<String>) {
    Outer().Inner().test()
}

class Outer {
    fun foo() = 1

    inner class Inner {
        fun innerFun() = 39
        fun test() {
            fun f() = 156

            // outer is captured in lambda
            lambda {
                // EXPRESSION: foo() + 1
                // RESULT: 2: I
                //Breakpoint!
                val a = foo()
            }

            // outer isn't captured in lambda
            lambda {
                // EXPRESSION: foo() + 2
                // RESULT: 'this@Outer' is not captured
                //Breakpoint!
                val a = 1
            }

            // inner is captured in lambda
            lambda {
                // EXPRESSION: foo() + 3
                // RESULT: 4: I
                //Breakpoint!
                val a = innerFun()
            }
        }
    }
}

fun lambda(f: () -> Unit) = f()

// IGNORE_K2