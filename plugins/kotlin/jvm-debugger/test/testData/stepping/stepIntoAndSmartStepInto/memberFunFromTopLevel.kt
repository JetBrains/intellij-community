package memberFunFromTopLevel

class A {
    fun bar() {
        val a = 1
    }
}

fun main(args: Array<String>) {
    val a = A()

    //Breakpoint!
    a.bar()
}

// IGNORE_K2_SMART_STEP_INTO