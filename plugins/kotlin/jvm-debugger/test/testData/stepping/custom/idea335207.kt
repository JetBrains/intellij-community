package idea335207

fun main() {
    fun localFun() {
        // SMART_STEP_INTO_BY_INDEX: 2
        //Breakpoint!
        Any().let {
            it
        }
    }
    localFun()
}

// IGNORE_K2
