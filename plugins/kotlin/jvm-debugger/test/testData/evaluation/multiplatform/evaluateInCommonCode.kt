// MODULE: common
// FILE: common.kt

expect fun foo(): Int

fun bar() {
    //Breakpoint1
    foo()
    //Breakpoint2
    foo()
}

// ADDITIONAL_BREAKPOINT: common.kt / Breakpoint1 / line / 1
// ADDITIONAL_BREAKPOINT: common.kt / Breakpoint2 / line / 1

// EXPRESSION: foo()
// RESULT: 1: I

// EXPRESSION: listOf(1, 2, 3).map { it }.size
// RESULT: 3: I

// MODULE: jvm
// FILE: evaluateInCommonCode.kt

actual fun foo() = 1

fun main() {
    bar()
}
