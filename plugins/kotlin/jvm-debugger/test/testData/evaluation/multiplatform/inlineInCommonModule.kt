// MODULE: common
// FILE: common.kt

inline fun foo(): Int = 42

fun boo() {
    "".toString()
}

// MODULE: jvm(common)
// FILE: jvm.kt

fun main() {
    //Breakpoint!
    boo()
}

// STEP_INTO: 1
// EXPRESSION: foo()
// RESULT: 42: I

// IGNORE_K2
