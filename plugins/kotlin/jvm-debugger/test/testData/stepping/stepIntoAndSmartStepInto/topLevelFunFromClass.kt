package topLevelFunFromClass

class A {
    fun bar() {
        //Breakpoint!
        foo()
    }
}

fun foo() {
    val a = 1
}


fun main(args: Array<String>) {
    A().bar()
}

// IGNORE_K2_SMART_STEP_INTO