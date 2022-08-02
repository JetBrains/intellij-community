package classObjectFunFromClass

class A {
    fun bar() {
        //Breakpoint!
        foo()
    }

    companion object {
        fun foo() {
            val a = 1
        }
    }
}

fun main(args: Array<String>) {
    A().bar()
}

// IGNORE_K2_SMART_STEP_INTO