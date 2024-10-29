fun main() {
    //Breakpoint!
    val x = 1
}

inline fun Int.extInlineGenericFun() = this

fun notUsedFun() {
    val newName = ::someFun
    val ret = newName()
}

fun someFun() = 1

open class A {
    val prop = 43
}

class B: A()

// EXPRESSION: 42.extInlineGenericFun()
// RESULT: 42: I

// EXPRESSION: B().prop
// RESULT: 43: I