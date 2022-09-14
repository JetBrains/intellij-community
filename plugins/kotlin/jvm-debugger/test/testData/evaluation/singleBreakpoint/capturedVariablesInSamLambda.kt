package capturedVariablesInSamLambda

fun invoke(runnable: Runnable) {
    runnable.run()
}

class A {
    val a = 1
}

class B {
    val b = 1
}

fun main() {
    val a = A()
    val b = B()
    invoke {
        // EXPRESSION: a
        // RESULT: instance of capturedVariablesInSamLambda.A(id=ID): LcapturedVariablesInSamLambda/A;
        // EXPRESSION: b
        // RESULT: instance of capturedVariablesInSamLambda.B(id=ID): LcapturedVariablesInSamLambda/B;
        // EXPRESSION: a.a
        // RESULT: 1: I
        // EXPRESSION: b.b
        // RESULT: 1: I
        //Breakpoint!
        println(a)
        println(b)
    }
}

// PRINT_FRAME
// SHOW_KOTLIN_VARIABLES
