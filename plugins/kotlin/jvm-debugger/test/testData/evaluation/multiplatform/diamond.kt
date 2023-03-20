// MODULE: common
// FILE: common.kt
// PLATFORM: common
expect fun debugMe(i: Int): Int

fun commonContext(){
    //Breakpoint1
    val str = "Stop here"
}

expect fun left(): Int
expect fun right(): Int

// ADDITIONAL_BREAKPOINT: common.kt / Breakpoint1 / line / 1

// EXPRESSION: debugMe(3)
// RESULT: 6: I

// EXPRESSION: left()
// RESULT: 1: I

// EXPRESSION: right()
// RESULT: 2: I

// MODULE: left
// FILE: left.kt
// PLATFORM: jvm
// DEPENDS_ON: common
expect fun leftImplInLeaf(): Int
actual fun left(): Int = leftImplInLeaf()

// MODULE: right
// FILE: right.kt
// PLATFORM: jvm
// DEPENDS_ON: common
actual fun right(): Int = rightImplInLeaf()
expect fun rightImplInLeaf(): Int

// MODULE: jvm
// FILE: jvm.kt
// PLATFORM: jvm
// DEPENDS_ON: left, right
actual fun debugMe(i: Int): Int {
    return left() + right() + i
}

// FILE: leftImpl.kt
actual fun leftImplInLeaf(): Int = 1

// FILE: rightImpl.kt
actual fun rightImplInLeaf(): Int = 2

// FILE: main.kt
fun main(){
    commonContext()
}
