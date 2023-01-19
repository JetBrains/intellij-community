package lambdaOnSecondLine

fun main(args: Array<String>) {
    val a = A()
    // EXPRESSION: it
    // RESULT: 1: I
    // STEP_INTO: 2
    a.foo { a }
        //Breakpoint! (lambdaOrdinal = -1)
        .foo { a }
}

class A {
    fun foo(f: (Int) -> A): A {
        return f(1)
    }
}

