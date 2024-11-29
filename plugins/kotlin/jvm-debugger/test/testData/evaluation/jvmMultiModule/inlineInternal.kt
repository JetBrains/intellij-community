// This test is supposed to cover KT-72930, but at the moment it does not:
// for the tests in evaluation/jvmMultiModule we create the single source session for the files from all the JVM modules.
// Thus, the problem actually does not reproduce on the test.
// The fix is verified manually though.
// We should align test behaviour with the actual IDE behaviour: KTIJ-32229

// MODULE: jvm-lib
// FILE: decl.kt

import kotlin.reflect.KProperty

internal inline fun topLevelFunc(bar: () -> Int) = bar()

internal inline val topLevelProperty: Int
    get() {
        return 2
    }

class C {
    internal inline fun memberFunc(bar: () -> Int) = bar()

    internal inline val memberProperty: Int
        get() {
            return 4
        }

    internal inline operator fun getValue(nothing: Nothing?, property: KProperty<*>): Int {
        return 5
    }
}

// MODULE: jvm-app(jvm-lib)
// FILE: call.kt
// DEPENDS_ON: common

public fun main() {
    val c = C()

    // EXPRESSION: topLevelFunc{1}
    // RESULT: 1: I
    //Breakpoint!
    "".toString()

    // EXPRESSION: topLevelProperty
    // RESULT: 2: I
    //Breakpoint!
    "".toString()

    // EXPRESSION: c.memberFunc{3}
    // RESULT: 3: I
    //Breakpoint!
    "".toString()

    // EXPRESSION: c.memberProperty
    // RESULT: 4: I
    //Breakpoint!
    "".toString()

    // EXPRESSION: val delegate by C(); delegate
    // RESULT: 5: I
    //Breakpoint!
    "".toString()
}