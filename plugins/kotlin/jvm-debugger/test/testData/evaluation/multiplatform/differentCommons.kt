// MODULE: left
// FILE: left.kt
// PLATFORM: common
expect fun left(): Int

fun debugTrulyMultiplatformCommon() {
    //Breakpoint1
    val s = "Left stop"
}

// ADDITIONAL_BREAKPOINT: left.kt / Breakpoint1 / line / 1

// EXPRESSION: left()
// RESULT: 1: I


// MODULE: right
// FILE: right.kt
// PLATFORM: jvm
expect fun right(): Int

fun debugSharedJvmCommon() {
    //Breakpoint2
    val s = "Right stop"
}

// ADDITIONAL_BREAKPOINT: right.kt / Breakpoint2 / line / 1

// EXPRESSION: right()
// RESULT: 2: I


// MODULE: jvm
// FILE: jvm.kt
// PLATFORM: jvm
// DEPENDS_ON: left, right
actual fun left(): Int = 1
actual fun right(): Int = 2

fun main(){
    debugTrulyMultiplatformCommon()
    debugSharedJvmCommon()
}
