// FILE: inlineFunctionDeepInliningSameFile.kt
package inlineFunctionDeepInliningSameFile

fun main() {
    //Breakpoint!
    val a = 1
}

// EXPRESSION: myFun1 { 50 }
// RESULT: 50: I

inline fun myFun1(function: () -> Int): Int {
    return myFun2(function)
}

inline fun myFun2(function: () -> Int): Int {
    return myFun3(function)
}

inline fun myFun3(function: () -> Int): Int {
    return myFun4(function)
}

inline fun myFun4(function: () -> Int): Int {
    return myFun5(function)
}

inline fun myFun5(function: () -> Int): Int {
    return myFun6(function)
}

inline fun myFun6(function: () -> Int): Int {
    return myFun7(function)
}

inline fun myFun7(function: () -> Int): Int {
    return myFun8(function)
}

inline fun myFun8(function: () -> Int): Int {
    return myFun9(function)
}

inline fun myFun9(function: () -> Int): Int {
    return myFun10(function)
}

inline fun myFun10(function: () -> Int): Int {
    return function()
}
