// FILE: Alpha.kt

inline fun inlineFun() = 1

// FILE: Bravo.kt

fun main() {
    fun localFun() = 42
    inlineFun()
    //Breakpoint!
    val x = 1
}

// EXPRESSION: localFun()
// RESULT: 42: I