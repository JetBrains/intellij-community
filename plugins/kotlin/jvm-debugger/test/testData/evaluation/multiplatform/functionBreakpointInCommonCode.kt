// MODULE: common
// FILE: common.kt
// PLATFORM: common
expect fun foo(): Int

fun bar() {
    foo()
}

// ADDITIONAL_BREAKPOINT: common.kt / expect fun foo(): Int / fun

// EXPRESSION: x
// RESULT: 1: I

// MODULE: jvm(common)
// FILE: functionBreakpointInCommonCode.kt
// PLATFORM: jvm

val x = 1

actual fun foo(): Int {
    return x
}

fun main() {
    bar()
}
