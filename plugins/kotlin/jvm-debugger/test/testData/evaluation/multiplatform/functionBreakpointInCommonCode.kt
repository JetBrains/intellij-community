// MODULE: common
// FILE: common.kt
// PLATFORM: common
expect fun foo(): Int

fun bar() {
    foo()
}

// ADDITIONAL_BREAKPOINT: common.kt / expect fun foo(): Int / fun / 1

// EXPRESSION: x
// RESULT: 1: I

// MODULE: jvm
// FILE: functionBreakpointInCommonCode.kt
// PLATFORM: jvm
// DEPENDS_ON: common
val x = 1

actual fun foo(): Int {
    return x
}

fun main() {
    bar()
}
