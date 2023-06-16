package smartStepIntoFunWithInlineParameter

fun main() {
    //Breakpoint!
    f1(0) + f2(0u)
}
fun f1(i: Int) = 42
fun f2(u: UInt) = 43

// SMART_STEP_INTO_BY_INDEX: 2
// IGNORE_K2
