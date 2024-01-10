package smartStepIntoLambdaWithparametersDestructuring

fun main() {
    testWithIndex()
    testNoDestruction()
    testDestruction()
    testInlineDestruction()
}

fun testWithIndex() {
    // STEP_OVER: 1
    //Breakpoint!
    val list = listOf(1, 2, 3)

    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    list.withIndex().find { (i, value) ->
        i + 1 > value
    }
    // RESUME: 1
}

fun testNoDestruction() {
    // STEP_OVER: 1
    //Breakpoint!
    val a = Ax(0, 0)
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    a.foo { x ->
        println("$x")
    }
    // RESUME: 1
}

fun testDestruction() {
    // STEP_OVER: 1
    //Breakpoint!
    val a = Ax(0, 0)
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    a.foo { (x, y) ->
        println("$x$y")
    }
    // RESUME: 1
}

fun testInlineDestruction() {
    // STEP_OVER: 1
    //Breakpoint!
    val a = Ax(0, 0)
    // SMART_STEP_INTO_BY_INDEX: 2
    // STEP_OVER: 1
    a.inlineFoo { (x, y) ->
        println("$x$y")
    }
    // RESUME: 1
}

fun Ax.foo(f: (Ax) -> Unit) = f(this)
inline fun Ax.inlineFoo(f: (Ax) -> Unit) = f(this)
data class Ax(val x: Int, val y: Int)

// IGNORE_K2
