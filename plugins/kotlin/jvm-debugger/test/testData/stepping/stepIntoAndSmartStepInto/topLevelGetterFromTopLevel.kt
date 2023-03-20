package topLevelGetterFromTopLevel

val bar: Int
    get() {
        val a = 1
        return 1
    }

fun main(args: Array<String>) {
    //Breakpoint!
    bar
}

// IGNORE_K2_SMART_STEP_INTO