// This test is supposed to cover KT-73912, but at the moment the problem does not reproduce in test setup
// See KTIJ-32553

// ATTACH_LIBRARY: maven(org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3)

// MODULE: common
// FILE: common.kt

import kotlinx.serialization.Serializable

inline fun inlineFun(lambda : () -> Int) = lambda()

@Serializable
class A

// MODULE: jvm(common)
// FILE: jvm.kt

fun main() {
    // EXPRESSION: inlineFun{ 42 }
    // RESULT: 42: I
    //Breakpoint!
    val x = 1
}
