// FILE: inlineFunction.kt
package inlineFunctionDeepInlining
import libOne.*

fun main(args: Array<String>) {
    //Breakpoint!
    val a = 1
}

// EXPRESSION: myFun { 1 }
// RESULT: 1: I

// FILE: libOne.kt
package libOne
import libTwo.*
inline fun myFun(f: () -> Int): Int = myFun2(f)

// FILE: libTwo.kt
package libTwo
import libThree.*
inline fun myFun2(f: () -> Int): Int = myFun3(f)

// FILE: libThree.kt
package libThree
import libFour.*
inline fun myFun3(f: () -> Int): Int = myFun4(f)

// FILE: libFour.kt
package libFour
import libFive.*
inline fun myFun4(f: () -> Int): Int = myFun5(f)

// FILE: libFive.kt
package libFive
import libSix.*
inline fun myFun5(f: () -> Int): Int = myFun6(f)

// FILE: libSix.kt
package libSix
import libSeven.*
inline fun myFun6(f: () -> Int): Int = myFun7(f)

// FILE: libSeven.kt
package libSeven
import libEight.*
inline fun myFun7(f: () -> Int): Int = myFun8(f)

// FILE: libEight.kt
package libEight
import libNine.*
inline fun myFun8(f: () -> Int): Int = myFun9(f)

// FILE: libNine.kt
package libNine
import libTen.*
inline fun myFun9(f: () -> Int): Int = myFun10(f)

// FILE: libTen.kt
package libTen
//import libEleven.*
inline fun myFun10(f: () -> Int): Int = f()


