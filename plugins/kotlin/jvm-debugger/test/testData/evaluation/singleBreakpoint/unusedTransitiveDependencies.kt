// FILE: unusedTransitiveDependencies.kt
package unusedTransitiveDependencies
import libOne.*

fun main(args: Array<String>) {
    //Breakpoint!
    val a = 1
}

// EXPRESSION: noninlineFunction { 1 }
// RESULT: 1: I

// FILE: libOne.kt
package libOne

import libTwo.*

fun noninlineFunction(f: () -> Int): Int = f()


fun unusedFunction(): Int {
    return inlineFunction()
}

inline fun inlineFunction(): Int {
    return unusedFunction2()
}

// FILE: libTwo.kt
package libTwo
import libThree.*

fun unusedFunction2(): Int {
    return inlineFunction2()
}

inline fun inlineFunction2(): Int {
    return unusedFunction3()
}


// FILE: libThree.kt
package libThree
import libFour.*
fun unusedFunction3(): Int {
    return inlineFunction3()
}
inline fun inlineFunction3(): Int {
    return unusedFunction4()
}

// FILE: libFour.kt
package libFour
import libFive.*
fun unusedFunction4(): Int {
    return inlineFunction4()
}
inline fun inlineFunction4(): Int {
    return unusedFunction5()
}

// FILE: libFive.kt
package libFive
import libSix.*
fun unusedFunction5(): Int {
    return inlineFunction5()
}
inline fun inlineFunction5(): Int {
    return unusedFunction6()
}

// FILE: libSix.kt
package libSix
import libSeven.*
fun unusedFunction6(): Int {
    return inlineFunction6()
}
inline fun inlineFunction6(): Int {
    return unusedFunction7()
}

// FILE: libSeven.kt
package libSeven
import libEight.*
fun unusedFunction7(): Int {
    return inlineFunction7()
}
inline fun inlineFunction7(): Int {
    return unusedFunction8()
}

// FILE: libEight.kt
package libEight
import libNine.*
fun unusedFunction8(): Int {
    return inlineFunction8()
}
inline fun inlineFunction8(): Int {
    return unusedFunction9()
}

// FILE: libNine.kt
package libNine
import libTen.*
fun unusedFunction9(): Int {
    return inlineFunction9()
}
inline fun inlineFunction9(): Int {
    return unusedFunction10()
}

// FILE: libTen.kt
package libTen
import libEleven.*
fun unusedFunction10(): Int {
    return inlineFunction10()
}
inline fun inlineFunction10(): Int {
    return unusedFunction11()
}

// FILE: libEleven.kt
package libEleven
import libTwelve.*
fun unusedFunction11(): Int {
    return inlineFunction11()
}
inline fun inlineFunction11(): Int {
    return unusedFunction12()
}

// FILE: libTwelve.kt
package libTwelve
fun unusedFunction12(): Int {
    return inlineFunction12()
}
inline fun inlineFunction12(): Int {
    return 1
}