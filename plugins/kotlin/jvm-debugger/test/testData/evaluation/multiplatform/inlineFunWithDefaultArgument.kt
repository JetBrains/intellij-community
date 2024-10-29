// IGNORE_K2
// MODULE: common
// FILE: common.kt

expect inline fun foo(param: () -> Int = { 1 }): Int

// MODULE: jvm(common)
// FILE: jvm.kt

actual inline fun foo(param: () -> Int): Int = param()

fun main() {
    // EXPRESSION: foo()
    // RESULT: 1: I
    //Breakpoint!
    val a = 0

    // EXPRESSION: foo({2})
    // RESULT: 2: I
    //Breakpoint!
    val b = 0
}