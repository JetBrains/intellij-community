package smartStepIntoDeferredLambdas

import kotlin.concurrent.thread

typealias FunT = () -> Unit

fun MutableList<FunT>.foo(f: FunT): MutableList<FunT> {
    add(f)
    return this
}

fun MutableList<FunT>.bar(f: FunT): MutableList<FunT> {
    add(f)
    return this
}

fun putLambdaInACollection(list: MutableList<FunT>, f: FunT) {
    list.add(f)
}

fun MutableList<FunT>.invokeAndClear() {
    forEach { it.invoke() }
    clear()
}

fun main() {
    // STEP_OVER: 1
    //Breakpoint!
    val list = mutableListOf<FunT>()

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    list.foo { println(1) }
        .bar { println(2) }
        .foo { println(3) }
    list.invokeAndClear()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 4
    // RESUME: 1
    list.foo { println(1) }
        .bar { println(2) }
        .foo { println(3) }
    list.invokeAndClear()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 6
    // RESUME: 1
    list.foo { println(1) }
        .bar { println(2) }
        .foo { println(3) }
    list.invokeAndClear()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    putLambdaInACollection(list) { println(4) }
    list.invokeAndClear()

    // STEP_OVER: 1
    //Breakpoint!
    stopHere()

    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    putLambdaInACollection(list) { println(4) }
    val thread = thread(true) { list.invokeAndClear() }
    thread.join()
}

fun stopHere() {

}
