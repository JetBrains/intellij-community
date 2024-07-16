package stepThroughSynchronized

fun main(args: Array<String>) {
    //Breakpoint!
    val x = 10
    val y = synchronized(args) {
        args.size * 2
    }
    val z = 30
}

// STEP_OVER: 99
// REGISTRY: debugger.kotlin.step.through.inline.lambdas=true
