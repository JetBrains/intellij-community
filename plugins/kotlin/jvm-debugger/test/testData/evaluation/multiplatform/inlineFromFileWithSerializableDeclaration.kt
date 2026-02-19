// This test is supposed to cover KT-73912, but at the moment the problem does not reproduce in test setup
// See KTIJ-32553

// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-serialization-json-1.7.3.jar)
// ATTACH_LIBRARY_BY_LABEL: classes(@kotlin_test_deps//:kotlinx-serialization-core-jvm-1.7.3.jar)

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
