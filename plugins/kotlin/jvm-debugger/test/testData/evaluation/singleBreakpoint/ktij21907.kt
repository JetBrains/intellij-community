

// FILE: ktij21907.kt
package ktij21907
import libOne.*

val x = inlineFunction { 1 }

fun main(args: Array<String>) {
    //Breakpoint!
    val a = 1
}

// EXPRESSION: args.size
// RESULT: 0: I

// FILE: libOne.kt
package libOne

inline fun inlineFunction(block: () -> Int): Int {
    return block()
}


