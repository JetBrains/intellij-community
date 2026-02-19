package stepThroughMyFunc

fun main(args: Array<String>) {
    //Breakpoint!
    val x = 10
    val y = myFunc {
        20
    }
    val z = 30
}

inline fun <R> myFunc(block: () -> R) {
    val aa = 100
    block()
    val bb = 200
}

// STEP_OVER: 99
// REGISTRY: debugger.kotlin.step.through.inline.lambdas=true
