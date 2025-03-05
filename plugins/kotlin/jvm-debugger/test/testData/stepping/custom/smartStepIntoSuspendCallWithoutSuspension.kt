package smartStepIntoSuspendCallWithoutSuspension

fun getInt() = 42
fun execute(x: Int) = Unit

suspend fun extracted() {
    // SMART_STEP_INTO_BY_INDEX: 1
    // REUSME: 1
    //Breakpoint!
    execute(getInt())
}

suspend fun main() {
    extracted()
}
