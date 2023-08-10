package extFun

class A

fun A.bar() {
    val a = 1
}

fun main(args: Array<String>) {
    val a = A()
    //Breakpoint!
    a.bar()
}

// IGNORE_K2_SMART_STEP_INTO