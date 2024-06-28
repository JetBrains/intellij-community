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

// EXPRESSION: 42.extInlineGenericFun()
// RESULT: 42: I