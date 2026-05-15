// IDEA-345678
package smartStepIntoInlineLambdaReceiverParameter

inline fun boo(block: () -> Unit) = block().also { println() }

fun main() {
    val x = 1
    // STEP_INTO: 1
    // SMART_STEP_INTO_BY_INDEX: 1
    // STEP_OVER: 1
    //Breakpoint!
    boo { println(x) }
    println(x)
}
