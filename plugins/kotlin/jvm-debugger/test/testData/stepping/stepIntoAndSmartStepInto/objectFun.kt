package objectFun

object A {
    fun bar() {
        val a = 1
    }
}

fun main(args: Array<String>) {
    //Breakpoint!
    A.bar()
}

// IGNORE_K2_SMART_STEP_INTO