// This test is supposed to cover KT-72930, but at the moment it does not:
// for the tests in evaluation/jvmMultiModule we create the single source session for the files from all the JVM modules.
// Thus, the problem actually does not reproduce on the test.
// The fix is verified manually though.
// We should align test behaviour with the actual IDE behaviour: KTIJ-32229

// MODULE: jvm-lib
// FILE: decl.kt

import kotlin.reflect.KProperty

class C {
    internal inline operator fun getValue(nothing: Nothing?, property: KProperty<*>): Int {
        return 5
    }
}

// MODULE: jvm-app(jvm-lib)
// FILE: call.kt
// DEPENDS_ON: common

public fun main() {
    val c = C()
    "".toString()

    // EXPRESSION: val delegate by C(); delegate
    // RESULT: 5: I
    //Breakpoint!
    "".toString()
}

// IGNORE_K1
