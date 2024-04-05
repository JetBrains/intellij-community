package stepThroughForEach

fun main(args: Array<String>) {
    //Breakpoint!
    val x = 10
    listOf(1, 2, 3).forEach {
        println(it)
    }
    val z = 30
}

// STEP_OVER: 99
// REGISTRY: debugger.kotlin.step.through.inline.lambdas=true
