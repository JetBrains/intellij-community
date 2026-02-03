class A(internal val internalProp: Int) {
    internal fun internalFun(): Int = 2
}

fun applyLambda(d: A, lambda: (A) -> Int) = lambda(d)

fun main() {
    //Breakpoint!
    val x = 1
}

// EXPRESSION: applyLambda(A(1)) { it.internalProp }
// RESULT: 1: I

// EXPRESSION: applyLambda(A(1)) { it.internalFun() }
// RESULT: 2: I