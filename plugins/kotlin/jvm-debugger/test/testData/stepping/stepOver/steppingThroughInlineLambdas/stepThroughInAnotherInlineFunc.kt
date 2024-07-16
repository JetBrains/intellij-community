package stepThroughInAnotherInlineFunc

inline fun deepest() {
    myFunc {
        //Breakpoint!
        val x = 10
    }
    val y = 20
}

inline fun deep() {
    deepest()
    val z = 30
}

fun high() {
    deep()
}

fun main(args: Array<String>) {
    high()
}

inline fun <R> myFunc(block: () -> R) {
    val aa = 100
    block()
    val bb = 200
}

// STEP_OVER: 99
// REGISTRY: debugger.kotlin.step.through.inline.lambdas=true
