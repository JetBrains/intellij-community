package kt15259

interface ObjectFace

private fun makeFace() = object : ObjectFace {
    //Breakpoint!
    init { 5 }
}

fun main() {
    makeFace()
}

// STEP_OVER: 1

// EXPRESSION: this
// RESULT: 'this' is not defined in this context

// TODO: Muted on the IR Evaluator
// This is affected by additional steps in initializers by the IR backend.