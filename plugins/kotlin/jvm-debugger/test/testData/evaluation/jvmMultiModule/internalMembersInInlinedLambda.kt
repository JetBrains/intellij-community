// MODULE: jvm-lib
// FILE: decl.kt

class A(internal val internalProp: Int) {
    internal fun internalFun(): Int = 2
}

fun applyLambda(d: A, lambda: (A) -> Int) = lambda(d)

// MODULE: jvm-app(jvm-lib)
// FILE: call.kt

fun main() {
    // EXPRESSION: applyLambda(A(1)) { it.internalProp }
    // RESULT: 1: I
    //Breakpoint!
    val x = 0

    // EXPRESSION: applyLambda(A(1)) { it.internalFun() }
    // RESULT: 2: I
    //Breakpoint!
    val y = 0
}



